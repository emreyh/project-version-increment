package com.emreyh.projectversionincrement.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class IncrementVersionAction extends AnAction {

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) {
      return;
    }
    updateVersionNumber(project);
  }

  private void updateVersionNumber(Project project) {
    String chartFilePath = project.getBasePath() + "/helm/Chart.yaml";
    File chartFile = new File(chartFilePath);
    if (!chartFile.exists()) {
      System.out.println("Chart.yaml file does not exist");
      return;
    }

    try {
      // Read the contents of the Chart.yaml file
      String chartFileContents = new String(Files.readAllBytes(chartFile.toPath()));

      // Extract current version from Chart.yaml
      String currentVersion = extractVersionFromChartYaml(chartFileContents);
      if (currentVersion == null) {
        System.out.println("Invalid version format in Chart.yaml");
        return;
      }

      // Increment version number
      String newVersion = incrementVersion(currentVersion);

      // Update Chart.yaml
      chartFileContents = updateVersionInChartYaml(chartFileContents, newVersion);
      Files.write(chartFile.toPath(), chartFileContents.getBytes());

      // Update pom.xml
      updatePomVersion(project, newVersion);

      // Refresh the files in IntelliJ IDEA
      refreshFile(chartFile);
      refreshFile(getPomFile(project));

    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private String extractVersionFromChartYaml(String chartFileContents) {
    String versionTag = "version:";
    int versionTagIndex = chartFileContents.indexOf(versionTag);
    if (versionTagIndex != -1) {
      int versionStartIndex = versionTagIndex + versionTag.length();
      int versionEndIndex = chartFileContents.indexOf('\n', versionStartIndex);
      if (versionEndIndex != -1) {
        return chartFileContents.substring(versionStartIndex, versionEndIndex).trim();
      }
    }
    return null;
  }

  private String incrementVersion(String currentVersion) {
    String[] versionArray = currentVersion.split("\\.");
    if (versionArray.length == 3) {
      int majorVersion = Integer.parseInt(versionArray[0]);
      int minorVersion = Integer.parseInt(versionArray[1]);
      int patchVersion = Integer.parseInt(versionArray[2]);

      if (patchVersion < 100) {
        versionArray[2] = String.valueOf(++patchVersion);
      } else if (minorVersion < 100) {
        versionArray[1] = String.valueOf(++minorVersion);
        versionArray[2] = "0";
      } else {
        versionArray[0] = String.valueOf(++majorVersion);
        versionArray[1] = "0";
        versionArray[2] = "0";
      }

      return String.join(".", versionArray);
    }
    return currentVersion;
  }

  private String updateVersionInChartYaml(String chartFileContents, String newVersion) {
    String versionTag = "version:";
    String appVersionTag = "appVersion:";

    // Replace the version field with the new version number
    chartFileContents =
        chartFileContents.replaceAll("(?m)^" + versionTag + ".*$", versionTag + " " + newVersion);

    // Replace the appVersion field with the new version number
    chartFileContents = chartFileContents.replaceAll("(?m)^" + appVersionTag + ".*$",
        appVersionTag + " " + newVersion);

    return chartFileContents;
  }


  private void updatePomVersion(Project project, String newVersion) {
    File pomFile = getPomFile(project);
    if (pomFile != null && pomFile.exists()) {
      try {
        Document document = getDocument(pomFile);
        Node versionNode = getVersionNode(document);
        if (versionNode != null) {
          versionNode.setTextContent(newVersion);
          writePom(pomFile, document);
          reformatPom(project, pomFile);
        } else {
          System.out.println("Version node not found in pom.xml");
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    } else {
      System.out.println("pom.xml file does not exist");
    }
  }

  private File getPomFile(Project project) {
    String projectPath = project.getBasePath();
    if (projectPath != null) {
      return new File(projectPath + "/pom.xml");
    }
    return null;
  }

  private void writePom(File pomFile, Document document) throws TransformerException {
    TransformerFactory transformerFactory = TransformerFactory.newInstance();
    Transformer transformer = transformerFactory.newTransformer();
    DOMSource source = new DOMSource(document);
    StreamResult result = new StreamResult(pomFile);
    transformer.transform(source, result);
  }

  private Node getVersionNode(Document document) throws XPathExpressionException {
    XPathFactory xpathFactory = XPathFactory.newInstance();
    XPath xpath = xpathFactory.newXPath();
    XPathExpression expr = xpath.compile("/project/version");
    return (Node) expr.evaluate(document, XPathConstants.NODE);
  }

  private Document getDocument(File pomFile)
      throws ParserConfigurationException, SAXException, IOException {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    DocumentBuilder builder = factory.newDocumentBuilder();
    return builder.parse(pomFile);
  }

  private void reformatPom(Project project, File pomFile) {
    VirtualFile virtualFile = VcsUtil.getVirtualFile(pomFile);
    if (virtualFile != null) {
      VcsDirtyScopeManager.getInstance(project).dirDirtyRecursively(virtualFile);
      refreshFile(pomFile);
    }
  }

  private void refreshFile(File file) {
    VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByIoFile(file);
    if (virtualFile != null) {
      VfsUtil.markDirtyAndRefresh(false, true, true, virtualFile);
    }
  }
}
