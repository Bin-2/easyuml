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
import org.openide.awt.ActionReferences;

@ActionID(
        category = "Source",
        id = "org.uml.reveng.ReverseEngineerJ2SEAction"
)
@ActionRegistration(
        displayName = "#CTL_ReverseEngineerJ2SEAction"
)
@ActionReferences({
    //    @ActionReference(path = "Menu/Source", position = 50),
    @ActionReference(path = "Projects/org-netbeans-modules-java-j2seproject/Actions", position = 700)
})
@Messages("CTL_ReverseEngineerJ2SEAction=easyUML Create Class Diagram")
/**
 * Class that responds to Reverse Engineer command from Java Project context
 * menu
 *
 * @author Milan Djoric
 */
public final class ReverseEngineerJ2SEAction extends ReverseEngineerActionBase implements ActionListener {

    private final Project context;

    /**
     * Default constructor
     *
     * @param context in which the command is issued
     */
    public ReverseEngineerJ2SEAction(Project context) {
        this.context = context;
    }

    /**
     * Main method for starting the Reverse engineering process.
     * <p>
     * Reads the project's configured main Java source folders, including custom
     * Ant source directories, then offers to save the generated diagram.
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
