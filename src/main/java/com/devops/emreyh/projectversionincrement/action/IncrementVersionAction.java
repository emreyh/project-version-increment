package com.devops.emreyh.projectversionincrement.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
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
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

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
    try {
      String projectPath = project.getBasePath();

      File pomFile = new File(projectPath + "/pom.xml");
      if (!pomFile.exists()) {
        System.out.println("pom.xml file is not exists");
        return;
      }

      Document document = getDocument(pomFile);
      Node versionNode = getVersionNode(document);
      String currentVersion = versionNode.getTextContent();

      String[] versionArray = currentVersion.split("\\.");
      if (versionArray.length != 3) {
        System.out.println(
            "Project version number not incremented. Because version number not valid.");
        return;
      }

      String newVersion = buildNewVersion(versionArray);
      versionNode.setTextContent(newVersion);

      writePom(pomFile, document);
      reformatPom(project, pomFile);

      // update Chart.yaml file
      updateChartYamlFile(project, newVersion);

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void writePom(File pomFile, Document document) throws TransformerException {
    // write the updated document to the pom file
    TransformerFactory transformerFactory = TransformerFactory.newInstance();
    Transformer transformer = transformerFactory.newTransformer();
    DOMSource source = new DOMSource(document);
    StreamResult result = new StreamResult(pomFile);
    transformer.transform(source, result);
  }

  @NotNull
  private String buildNewVersion(String[] versionArray) {
//    int majorVersion = Integer.parseInt(versionArray[0]);
    int minorVersion = Integer.parseInt(versionArray[1]);
    int patchVersion = Integer.parseInt(versionArray[2]);

    if (patchVersion < 100) {
      versionArray[2] = String.valueOf(++patchVersion);
    } else if (minorVersion < 100) {
      versionArray[1] = String.valueOf(++minorVersion);
    }
    return String.join(".", versionArray);
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
//    PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
//    WriteCommandAction.runWriteCommandAction(project, () -> {
//      CodeStyleManager.getInstance(project).reformat(psiFile);
//    });
    assert virtualFile != null;
    VcsDirtyScopeManager.getInstance(project).dirDirtyRecursively(virtualFile);
  }

  private void updateChartYamlFile(Project project, String newVersion) {
    String chartFilePath = project.getBasePath() + "/helm/Chart.yaml";
    File chartFile = new File(chartFilePath);
    if (!chartFile.exists()) {
      System.out.println("Chart.yaml file does not exist");
      return;
    }

    try {
      // Read the contents of the Chart.yaml file
      String chartFileContents = new String(Files.readAllBytes(chartFile.toPath()));

      // Replace the version and appVersion fields with the new version number
      chartFileContents =
          chartFileContents.replaceAll("(?m)^version:.*$", "version: " + newVersion);
      chartFileContents =
          chartFileContents.replaceAll("(?m)^appVersion:.*$", "appVersion: " + newVersion);

      // Write the updated contents back to the Chart.yaml file
      Files.write(chartFile.toPath(), chartFileContents.getBytes());

    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
