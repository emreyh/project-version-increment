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
import javax.xml.xpath.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Optional;

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
            Optional<String> newVersionNumberOpt = updatePomVersion(project);

            if (newVersionNumberOpt.isPresent()) {
                updateVersionInChartYaml(project.getBasePath(), newVersionNumberOpt.get());
            }
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
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

    private void updateVersionInChartYaml(String basePath, String newVersion) throws IOException {
        File chartFile = getChartFile(basePath);
        if (!chartFile.exists()) {
            System.out.println("Chart.yaml file not found. Skipped version increment operation.");
            return;
        }

        String chartFileContents = new String(Files.readAllBytes(chartFile.toPath()));

        String versionTag = "version:";
        String appVersionTag = "appVersion:";

        // Replace the version field with the new version number
        chartFileContents =
                chartFileContents.replaceAll("(?m)^" + versionTag + ".*$", versionTag + " " + newVersion);

        // Replace the appVersion field with the new version number
        chartFileContents = chartFileContents.replaceAll("(?m)^" + appVersionTag + ".*$",
                appVersionTag + " " + newVersion);

        Files.write(chartFile.toPath(), chartFileContents.getBytes());

        refreshFile(chartFile);
    }


    private Optional<String> updatePomVersion(Project project) {
        File pomFile = getPomFile(project.getBasePath());
        if (!pomFile.exists()) {
            System.out.println("pom.xml file does not exist");
            return Optional.empty();
        }

        try {
            Document document = getDocument(pomFile);
            Optional<Node> versionNode = getVersionNode(document);
            if (versionNode.isEmpty()) {
                System.out.println("Version node not found in pom.xml");
                return Optional.empty();
            }

            String versionProperty = versionNode.get().getTextContent();
            if (versionProperty.startsWith("${") && versionProperty.endsWith("}")) {
                String versionPropertyName = versionProperty.substring(2, versionProperty.length() - 1);
                versionNode = getVersionPropertyNode(document, versionPropertyName);
            }

            if (versionNode.isEmpty()) {
                System.err.println("The version node can not be empty. Skipped version increment operation.");
                return Optional.empty();
            }

            Node pomVersionNode = versionNode.get();
            String pomVersion = pomVersionNode.getTextContent();
            String incrementedPomVersion = incrementVersion(pomVersion);

            pomVersionNode.setTextContent(incrementedPomVersion);

            writePom(pomFile, document);
            reformatPom(project, pomFile);
            refreshFile(pomFile);

            return Optional.of(incrementedPomVersion);
        } catch (Exception e) {
            System.err.println(e.getMessage());
            return Optional.empty();
        }
    }

    private File getChartFile(String basePath) {
        return new File(basePath + "/helm/Chart.yaml");
    }

    private File getPomFile(String basePath) {
        return new File(basePath + "/pom.xml");
    }

    private void writePom(File pomFile, Document document) throws TransformerException {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        DOMSource source = new DOMSource(document);
        StreamResult result = new StreamResult(pomFile);
        transformer.transform(source, result);
    }

    private Optional<Node> getVersionNode(Document document) throws XPathExpressionException {
        XPathFactory xpathFactory = XPathFactory.newInstance();
        XPath xpath = xpathFactory.newXPath();
        XPathExpression expr = xpath.compile("/project/version");
        return Optional.ofNullable((Node) expr.evaluate(document, XPathConstants.NODE));
    }

    private Optional<Node> getVersionPropertyNode(Document document, String propertyName) {
        try {
            XPathFactory xpathFactory = XPathFactory.newInstance();
            XPath xpath = xpathFactory.newXPath();
            XPathExpression expr = xpath.compile("/project/properties/" + propertyName);
            return Optional.ofNullable((Node) expr.evaluate(document, XPathConstants.NODE));
        } catch (Exception e) {
            System.err.println("Version node not found.");
        }

        return Optional.empty();
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