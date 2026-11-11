package org.uml.filetype.cdg.wizard;

import org.netbeans.api.project.Project;
import org.uml.model.ClassDiagram;

/**
 *
 * @author Jelena
 */
public class ClassDiagramFilesFactory {

    Project currentProject;
    ClassDiagram classDiagram;
    String classDiagramName; // we should use/have some network ID instead name, since name may not be unigue
    // we need it to select that node in project tree upon network creation
    String createdFilePath;

    static final String FOLDER_NAME = "Class Diagrams";

    private static ClassDiagramFilesFactory instance;

    private ClassDiagramFilesFactory() {
    }

    public static ClassDiagramFilesFactory getDefault() {
        if (instance == null) {
            instance = new ClassDiagramFilesFactory();
        }
        return instance;
    }

    public void createClassDiagramFile(ClassDiagram cd) throws java.io.IOException {
        createdFilePath = null;
        java.nio.file.Path folder = java.nio.file.Paths.get(currentProject.getProjectDirectory().getPath(), FOLDER_NAME).toAbsolutePath().normalize();
        java.nio.file.Path target = folder.resolve(cd.getName() + ".cdg").normalize();
        if (!target.getParent().equals(folder)) {
            throw new java.io.IOException("Invalid diagram name");
        }
        if (java.nio.file.Files.exists(target)) {
            throw new java.io.IOException("Diagram already exists: " + target);
        }
        org.dom4j.Document document = org.dom4j.DocumentHelper.createDocument();
        org.dom4j.Element root = document.addElement("ClassDiagram").addAttribute("name", cd.getName());
        root.addElement("ClassDiagramComponents");
        root.addElement("ClassDiagramRelations");
        org.uml.model.AtomicFiles.write(target, document.asXML().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        createdFilePath = target.toString();
        currentProject.getProjectDirectory().refresh();
    }

    public String getCreatedFilePath() {
        return createdFilePath;
    }

    void setProject(Project project) {
        currentProject = project;
    }
}
