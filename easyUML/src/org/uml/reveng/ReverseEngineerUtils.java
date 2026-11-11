package org.uml.reveng;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.loaders.DataObjectNotFoundException;
import org.openide.nodes.Node;
import org.openide.util.Exceptions;
import org.uml.filetype.cdg.ClassDiagramDataObject;
import org.uml.jung.JUNGEngine;
import org.uml.model.ClassDiagram;
import org.uml.project.UMLProject;
import org.uml.visual.UMLTopComponent;
import org.uml.visual.widgets.ClassDiagramScene;

/**
 *
 * @author Boris Perović
 */
public class ReverseEngineerUtils {

    public static void saveDiagramAndOpenTopComponent(FileObject projectFolder, ClassDiagram classDiagram) {
        String defaultPath = projectFolder.getPath().replace("/", File.separator) + File.separator + UMLProject.CLASS_DIAGRAMS_FOLDER;
        String fileName = "";
        String fileExt = ".cdg";
        UMLTopComponent umlTopComponent;

        DateFormat dateFormat = new SimpleDateFormat("yyyy.MM.dd HH-mm-ss");
        Date date = new Date();
        fileName = classDiagram.getName() + " " + dateFormat.format(date) + fileExt;

        umlTopComponent = new UMLTopComponent(classDiagram, fileName);
        FileObject fileObject;
        try {
            fileObject = saveDiagramToFolder(defaultPath, umlTopComponent);
        } catch (IOException ex) {
            javax.swing.JOptionPane.showMessageDialog(null, ex.getMessage(), "Cannot save diagram",
                    javax.swing.JOptionPane.ERROR_MESSAGE);
            return;
        }
        // Do not open the umlTopComponent, but rather open the ClassDiagramDataObject,
        // so that new UMLTopComponent is tied to the file and the file correctly registers
        // events for renaming purposes
        try {
            DataObject dataObject = DataObject.find(fileObject);
            Node node = dataObject.getNodeDelegate();
            ClassDiagramDataObject cddo = node.getLookup().lookup(ClassDiagramDataObject.class);
            cddo.open();
        } catch (DataObjectNotFoundException ex) {
            Exceptions.printStackTrace(ex);
        }

    }

    public static void arrangeDiagram(ClassDiagramScene classDiagramScene) {
        //Make JUNG object
        JUNGEngine je = new JUNGEngine(classDiagramScene);
        //Apply it's layout to diagram (scene)
        je.applyJUNGLayout();
    }

    /**
     * Saves the generated diagram onto the selected path (inside a specified
     * folder)
     *
     * @param folderPath path to save diagram to
     * @param tc UMLTopComponent that this diagram belongs to
     */
    private static FileObject saveDiagramToFolder(String folderPath, UMLTopComponent tc) throws IOException {
        File diagramFile = new File(folderPath, tc.getName());
        if (diagramFile.exists()) {
            throw new IOException("Diagram already exists: " + diagramFile);
        }
        FileObject fileObject = FileUtil.createData(diagramFile);
        tc.setFileObject(fileObject);
        try {
            tc.saveTopComponent();
        } catch (IOException ex) {
            fileObject.delete();
            throw ex;
        }
        return fileObject;
    }
}
