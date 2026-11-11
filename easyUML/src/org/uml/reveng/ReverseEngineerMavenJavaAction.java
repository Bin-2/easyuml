package org.uml.reveng;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.Collection;
import org.netbeans.api.project.Project;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.util.NbBundle.Messages;
import org.uml.model.ClassDiagram;

@ActionID(
        category = "Source",
        id = "org.uml.reveng.ReverseEngineerMavenJavaAction"
)
@ActionRegistration(
        displayName = "#CTL_ReverseEngineerMavenJavaAction"
)
@ActionReference(path = "Projects/org-netbeans-modules-maven/Actions", position = 1050)
@Messages("CTL_ReverseEngineerMavenJavaAction=easyUML Create Class Diagram")
/**
 * Class that responds to Reverse Engineer command from Java Project context
 * menu
 *
 * @author Boris Perović
 */
public final class ReverseEngineerMavenJavaAction extends ReverseEngineerActionBase implements ActionListener {

    private final Project context;

    /**
     * Default constructor
     *
     * @param context in which the command is issued
     */
    public ReverseEngineerMavenJavaAction(Project context) {
        this.context = context;
    }

    /**
     * Main method for starting the Reverse engineering process.
     * <p>
     * Reads the project's configured main Java source folders, including custom
     * Maven source directories, then offers to save the generated diagram.
     *
     * @param ev action event that occurred (button pressed into main window)
     */
    @Override
    public void actionPerformed(ActionEvent ev) {
        try {
            Collection<File> files = ProjectJavaSources.mainSources(context);
            String diagramName = context.getProjectDirectory().getName();
            ClassDiagram classDiagram = ReverseEngineer.createClassDiagramFromFiles(files, diagramName);
            openReverseEngineerDialog(classDiagram);
        } catch (java.io.IOException ex) {
            javax.swing.JOptionPane.showMessageDialog(null, ex.getMessage(),
                    "Reverse engineering failed", javax.swing.JOptionPane.ERROR_MESSAGE);
        }
    }
}
