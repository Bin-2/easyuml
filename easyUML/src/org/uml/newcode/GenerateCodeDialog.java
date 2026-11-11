package org.uml.newcode;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import javax.lang.model.SourceVersion;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.netbeans.api.java.project.JavaProjectConstants;
import org.netbeans.api.java.queries.UnitTestForSourceQuery;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectManager;
import org.netbeans.api.project.ProjectUtils;
import org.netbeans.api.project.SourceGroup;
import org.netbeans.api.project.SourceGroupModifier;
import org.netbeans.api.project.ui.OpenProjects;
import org.netbeans.spi.project.ui.support.ProjectChooser;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.windows.WindowManager;
import org.uml.filetype.cdg.renaming.MyClassDiagramRenameTable;
import org.uml.model.ClassDiagram;

/**
 * Chooses a new Maven project or a configured source folder for missing files.
 *
 * @author Uros
 */
public class GenerateCodeDialog extends javax.swing.JDialog implements AutoCloseable {

    private final ClassDiagram classDiagram;
    private final JRadioButton newProject = new JRadioButton("Create a new Maven project", true);
    private final JRadioButton existingProject = new JRadioButton("Add missing files to an existing project");
    private final JTextField projectName = new JTextField(28);
    private final JTextField location = new JTextField(28);
    private final JTextField groupId = new JTextField("com.mycompany", 28);
    private final JTextField artifactId = new JTextField(28);
    private final JTextField javaVersion = new JTextField("8", 8);
    private final JTextField projectFolder = new JTextField(28);
    private final JCheckBox openProject = new JCheckBox("Open the new project in NetBeans", true);
    private final javax.swing.JComboBox<SourceTarget> cbxProjects = new javax.swing.JComboBox<>();
    private final JPanel destinations = new JPanel(new CardLayout());
    private String derivedArtifact;
    private boolean busy;

    // The rename parameter is retained for callers of the previous dialog API.
    public GenerateCodeDialog(ClassDiagram diagram, MyClassDiagramRenameTable renames) throws Exception {
        super(WindowManager.getDefault().getMainWindow(), true);
        classDiagram = diagram;
        initComponents();
        configureDestinations();
        buildComboBoxModel();
        updateMode();
        pack();
        setMinimumSize(getSize());
        getRootPane().setDefaultButton(btnGenerateCode);
    }

    private void configureDestinations() {
        ButtonGroup modes = new ButtonGroup();
        modes.add(newProject);
        modes.add(existingProject);
        JPanel choices = new JPanel(new FlowLayout(FlowLayout.LEADING));
        choices.add(newProject);
        choices.add(existingProject);
        newProject.addActionListener(event -> updateMode());
        existingProject.addActionListener(event -> updateMode());

        File defaultFolder = ProjectChooser.getProjectsFolder();
        location.setText(defaultFolder.getAbsolutePath());
        String base = suggestedProjectName(classDiagram.getName());
        String name = base;
        int suffix = 2;
        while (Files.exists(defaultFolder.toPath().resolve(name), LinkOption.NOFOLLOW_LINKS)) {
            name = base + "-" + suffix++;
        }
        projectName.setText(name);
        artifactId.setText(name);
        derivedArtifact = name;
        projectFolder.setEditable(false);

        JPanel newOptions = new JPanel(new GridBagLayout());
        addRow(newOptions, 0, "Project name", projectName);
        JPanel chooseLocation = new JPanel(new BorderLayout(6, 0));
        chooseLocation.add(location, BorderLayout.CENTER);
        javax.swing.JButton browse = new javax.swing.JButton("Browse...");
        browse.addActionListener(event -> chooseLocation());
        chooseLocation.add(browse, BorderLayout.EAST);
        addRow(newOptions, 1, "Parent folder", chooseLocation);
        addRow(newOptions, 2, "Group ID", groupId);
        addRow(newOptions, 3, "Artifact ID", artifactId);
        addRow(newOptions, 4, "Java version", javaVersion);
        addRow(newOptions, 5, "Project folder", projectFolder);
        addRow(newOptions, 6, "", openProject);
        addRow(newOptions, 7, "", new JLabel("Java packages are taken from the UML diagram."));

        JPanel existingOptions = new JPanel(new GridBagLayout());
        addRow(existingOptions, 0, "Java source folder", cbxProjects);
        addRow(existingOptions, 1, "", new JLabel("Existing files are skipped."));

        destinations.add(newOptions, "new");
        destinations.add(existingOptions, "existing");
        pnlDestination.add(choices, BorderLayout.NORTH);
        pnlDestination.add(destinations, BorderLayout.CENTER);
        DocumentListener listener = new DocumentListener() {
            public void insertUpdate(DocumentEvent event) { updateFolder(); }
            public void removeUpdate(DocumentEvent event) { updateFolder(); }
            public void changedUpdate(DocumentEvent event) { updateFolder(); }
        };
        projectName.getDocument().addDocumentListener(listener);
        location.getDocument().addDocumentListener(listener);
        updateFolder();
    }

    private static void addRow(JPanel panel, int row, String label, java.awt.Component control) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = row;
        constraints.anchor = GridBagConstraints.LINE_START;
        constraints.insets = new Insets(5, 8, 5, 8);
        JLabel caption = new JLabel(label);
        caption.setLabelFor(control);
        panel.add(caption, constraints);
        constraints.gridx = 1;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        panel.add(control, constraints);
    }

    private void updateFolder() {
        String derived = artifactName(projectName.getText());
        if (artifactId.getText().equals(derivedArtifact)) {
            artifactId.setText(derived);
        }
        derivedArtifact = derived;
        try {
            projectFolder.setText(Paths.get(location.getText()).resolve(projectName.getText()).toString());
        } catch (java.nio.file.InvalidPathException ex) {
            projectFolder.setText("Choose a valid folder.");
        }
    }

    private static String suggestedProjectName(String diagramName) {
        if (diagramName == null) {
            return "uml-project";
        }
        String name = diagramName.trim().replace('\\', '/').replaceAll("/+$", "");
        name = name.substring(name.lastIndexOf('/') + 1);
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".cdg") || lower.endsWith(".java")) {
            name = name.substring(0, name.lastIndexOf('.'));
        }
        // Older diagrams may store a qualified Java name or a path with dots.
        // This shortening only applies to the initial project suggestion.
        if (name.indexOf('.') >= 0 && SourceVersion.isName(name)) {
            name = name.substring(name.lastIndexOf('.') + 1);
        }
        return artifactName(name);
    }

    private static String artifactName(String name) {
        String value = name == null ? "" : name.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_.-]+", "-").replaceAll("^[.-]+|[.-]+$", "");
        return value.isEmpty() ? "uml-project" : value;
    }

    private void chooseLocation() {
        JFileChooser chooser = new JFileChooser(location.getText());
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Project parent folder");
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            location.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    public final void buildComboBoxModel() {
        cbxProjects.removeAllItems();
        for (Project project : OpenProjects.getDefault().getOpenProjects()) {
            SourceGroup[] groups = ProjectUtils.getSources(project)
                    .getSourceGroups(JavaProjectConstants.SOURCES_TYPE_JAVA);
            int before = cbxProjects.getItemCount();
            for (SourceGroup group : groups) {
                FileObject root = group.getRootFolder();
                File folder = FileUtil.toFile(root);
                if (folder != null && UnitTestForSourceQuery.findSources(root).length == 0) {
                    cbxProjects.addItem(new SourceTarget(project, folder.toPath(), null));
                }
            }
            if (cbxProjects.getItemCount() == before) {
                SourceGroupModifier.Future future = SourceGroupModifier.createSourceGroupFuture(
                        project, JavaProjectConstants.SOURCES_TYPE_JAVA, JavaProjectConstants.SOURCES_HINT_MAIN);
                if (future != null) {
                    cbxProjects.addItem(new SourceTarget(project, null, future));
                }
            }
        }
        cbxProjects.setPreferredSize(new Dimension(440, cbxProjects.getPreferredSize().height));
        existingProject.setEnabled(cbxProjects.getItemCount() > 0);
        if (cbxProjects.getItemCount() == 0) {
            existingProject.setToolTipText("Open a Java project to add missing files to it.");
        }
    }

    private void updateMode() {
        ((CardLayout) destinations.getLayout()).show(destinations, newProject.isSelected() ? "new" : "existing");
        btnGenerateCode.setEnabled(!busy && (newProject.isSelected() || cbxProjects.getItemCount() > 0));
    }

    private void generate() {
        try {
            if (newProject.isSelected()) {
                if (location.getText().trim().isEmpty()) {
                    throw new IOException("Choose a project parent folder.");
                }
                int targetJava;
                try {
                    targetJava = Integer.parseInt(javaVersion.getText().trim());
                } catch (NumberFormatException ex) {
                    throw new IOException("Enter a Java version number such as 8, 17 or 21.");
                }
                MavenProjectGenerator generator = new MavenProjectGenerator(classDiagram,
                        Paths.get(location.getText().trim()), projectName.getText().trim(),
                        groupId.getText().trim(), artifactId.getText().trim(), targetJava);
                Path directory = generator.getDirectory();
                runGeneration(() -> {
                    generator.createProject();
                    return "Created Maven project\n" + directory + "\n\nGenerated "
                            + generator.getSourceCount() + " Java files.";
                }, directory, openProject.isSelected(), true);
            } else {
                SourceTarget target = (SourceTarget) cbxProjects.getSelectedItem();
                if (target == null) {
                    throw new IOException("Select an open Java project's source folder.");
                }
                Path root = target.sourceRoot();
                List<Path> roots = new ArrayList<>();
                for (SourceGroup group : ProjectUtils.getSources(target.project)
                        .getSourceGroups(JavaProjectConstants.SOURCES_TYPE_JAVA)) {
                    File folder = FileUtil.toFile(group.getRootFolder());
                    if (folder != null) {
                        roots.add(folder.toPath());
                    }
                }
                ClassDiagramCodeGenerator.Plan plan = ClassDiagramCodeGenerator.prepare(classDiagram, root, roots);
                if (!plan.getSkipped().isEmpty() && !plan.getFiles().isEmpty()) {
                    String preview = "Create " + plan.getFiles().size() + " missing Java files?\n\n"
                            + fileList("Existing files to skip", plan.getSkipped());
                    Object[] options = {"Create missing files", "Cancel"};
                    if (JOptionPane.showOptionDialog(this, messageArea(preview), "Generate code",
                            JOptionPane.OK_CANCEL_OPTION, JOptionPane.INFORMATION_MESSAGE,
                            null, options, options[0]) != 0) {
                        return;
                    }
                }
                runGeneration(() -> {
                    ClassDiagramCodeGenerator.Result result = plan.writeMissing();
                    return fileList("Created files", result.getCreated()) + "\n"
                            + fileList("Skipped existing files", result.getSkipped());
                }, root, false, false);
            }
        } catch (IOException | IllegalArgumentException ex) {
            showError(ex);
        }
    }

    private void runGeneration(Callable<String> generate, Path directory, boolean open, boolean maven) {
        busy = true;
        btnGenerateCode.setEnabled(false);
        btnCancel.setEnabled(false);
        newProject.setEnabled(false);
        existingProject.setEnabled(false);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                String report = generate.call();
                FileUtil.refreshFor(directory.toFile());
                return report;
            }

            @Override
            protected void done() {
                busy = false;
                btnCancel.setEnabled(true);
                newProject.setEnabled(true);
                existingProject.setEnabled(cbxProjects.getItemCount() > 0);
                setDefaultCloseOperation(DISPOSE_ON_CLOSE);
                setCursor(Cursor.getDefaultCursor());
                updateMode();
                try {
                    String report = get();
                    if (maven) {
                        ProjectChooser.setProjectsFolder(directory.getParent().toFile());
                    }
                    int messageType = JOptionPane.INFORMATION_MESSAGE;
                    if (open) {
                        try {
                            FileObject folder = FileUtil.toFileObject(FileUtil.normalizeFile(directory.toFile()));
                            ProjectManager.getDefault().clearNonProjectCache();
                            Project project = folder == null ? null : ProjectManager.getDefault().findProject(folder);
                            if (project == null) {
                                throw new IOException("NetBeans could not recognize the generated Maven project.");
                            }
                            OpenProjects.getDefault().open(new Project[]{project}, false);
                        } catch (IOException | IllegalArgumentException ex) {
                            report += "\n\nThe project was created, but could not be opened.\n" + ex.getMessage();
                            messageType = JOptionPane.WARNING_MESSAGE;
                        }
                    }
                    JOptionPane.showMessageDialog(GenerateCodeDialog.this, messageArea(report),
                            "Code generation complete", messageType);
                    dispose();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    showError(ex);
                } catch (ExecutionException ex) {
                    showError(ex.getCause());
                }
            }
        }.execute();
    }

    private static String fileList(String title, List<Path> files) {
        StringBuilder text = new StringBuilder(title).append(" (").append(files.size()).append(")\n");
        for (Path file : files) {
            text.append(file).append('\n');
        }
        return text.toString();
    }

    private static JScrollPane messageArea(String message) {
        JTextArea text = new JTextArea(message);
        text.setEditable(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setCaretPosition(0);
        JScrollPane scroll = new JScrollPane(text);
        scroll.setPreferredSize(new Dimension(560, 240));
        return scroll;
    }

    private void showError(Throwable error) {
        JOptionPane.showMessageDialog(this, error.getMessage(), "Code generation failed", JOptionPane.ERROR_MESSAGE);
    }

    @Override
    public void close() {
        dispose();
    }

    private static final class SourceTarget {

        private final Project project;
        private final Path root;
        private final SourceGroupModifier.Future future;

        private SourceTarget(Project project, Path root, SourceGroupModifier.Future future) {
            this.project = project;
            this.root = root;
            this.future = future;
        }

        private Path sourceRoot() throws IOException {
            if (root != null) {
                return root;
            }
            SourceGroup group = future.createSourceGroup();
            File folder = group == null ? null : FileUtil.toFile(group.getRootFolder());
            if (folder == null) {
                throw new IOException("NetBeans could not create a local main Java source folder.");
            }
            return folder.toPath();
        }

        @Override
        public String toString() {
            return ProjectUtils.getInformation(project).getDisplayName() + " - "
                    + (root == null ? "Create main Java source folder" : root.toString());
        }
    }

    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        pnlDestination = new javax.swing.JPanel();
        btnGenerateCode = new javax.swing.JButton();
        btnCancel = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setTitle(org.openide.util.NbBundle.getMessage(GenerateCodeDialog.class, "GenerateCodeDialog.title"));
        pnlDestination.setLayout(new java.awt.BorderLayout());
        org.openide.awt.Mnemonics.setLocalizedText(btnGenerateCode, org.openide.util.NbBundle.getMessage(GenerateCodeDialog.class, "GenerateCodeDialog.btnGenerateCode.text"));
        btnGenerateCode.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnGenerateCodeActionPerformed(evt);
            }
        });
        org.openide.awt.Mnemonics.setLocalizedText(btnCancel, org.openide.util.NbBundle.getMessage(GenerateCodeDialog.class, "GenerateCodeDialog.btnCancel.text"));
        btnCancel.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnCancelActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(pnlDestination, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addComponent(btnGenerateCode)
                        .addGap(18, 18, 18)
                        .addComponent(btnCancel)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(pnlDestination, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGap(18, 18, 18)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(btnGenerateCode)
                    .addComponent(btnCancel))
                .addContainerGap())
        );
        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void btnGenerateCodeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnGenerateCodeActionPerformed
        generate();
    }//GEN-LAST:event_btnGenerateCodeActionPerformed

    private void btnCancelActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnCancelActionPerformed
        dispose();
    }//GEN-LAST:event_btnCancelActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnCancel;
    private javax.swing.JButton btnGenerateCode;
    private javax.swing.JPanel pnlDestination;
    // End of variables declaration//GEN-END:variables
}
