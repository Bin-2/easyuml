package org.uml.visual.dialogs;

import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import java.util.prefs.Preferences;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.openide.util.NbPreferences;
import org.openide.windows.WindowManager;
import org.uml.visual.DiagramExporter;
import org.uml.visual.DiagramExporter.Format;
import org.uml.visual.widgets.ClassDiagramScene;

/**
 * One format chooser; no persistent diagram settings or viewport-dependent
 * export modes.
 */
public final class ExportDiagramDialog {

    private ExportDiagramDialog() {
    }

    public static void show(ClassDiagramScene scene) {
        // The scene can extend beyond the screen; center dialogs on the IDE frame.
        final Frame parent = WindowManager.getDefault().getMainWindow();
        try {
            Rectangle bounds = DiagramExporter.bounds(scene);
            JComboBox<Format> formats = new JComboBox<>(Format.values());
            JComboBox<String> scales = new JComboBox<>(new String[]{"1×", "2×", "3×", "4×"});
            scales.setSelectedIndex(1);
            JLabel dimensions = new JLabel();
            Runnable update = () -> {
                boolean png = formats.getSelectedItem() == Format.PNG;
                scales.setEnabled(png);
                int scale = png ? scales.getSelectedIndex() + 1 : 1;
                dimensions.setText(png ? ((long) bounds.width * scale) + " × " + ((long) bounds.height * scale) + " pixels"
                        : bounds.width + " × " + bounds.height + " scene units (vector)");
            };
            formats.addActionListener(e -> update.run());
            scales.addActionListener(e -> update.run());
            update.run();
            JPanel options = new JPanel(new GridLayout(0, 2, 12, 8));
            options.add(new JLabel("Format:"));
            options.add(formats);
            options.add(new JLabel("PNG resolution:"));
            options.add(scales);
            options.add(new JLabel("Complete diagram:"));
            options.add(dimensions);
            if (JOptionPane.showConfirmDialog(parent, options, "Export Diagram",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
                return;
            }
            Format format = (Format) formats.getSelectedItem();
            int scale = scales.getSelectedIndex() + 1;
            if (format == Format.PNG) {
                DiagramExporter.pngSize(bounds, scale);
            }
            Preferences preferences = NbPreferences.forModule(ExportDiagramDialog.class);
            JFileChooser chooser = new JFileChooser(preferences.get("exportDirectory", System.getProperty("user.home")));
            chooser.setDialogTitle("Export Diagram");
            chooser.setAcceptAllFileFilterUsed(false);
            chooser.setFileFilter(new FileNameExtensionFilter(format.name() + " diagram", format.name().toLowerCase(java.util.Locale.ROOT)));
            chooser.setSelectedFile(new java.io.File("diagram." + format.name().toLowerCase(java.util.Locale.ROOT)));
            if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) {
                return;
            }
            Path target = DiagramExporter.withExtension(chooser.getSelectedFile().toPath(), format);
            if (java.nio.file.Files.exists(target) && JOptionPane.showConfirmDialog(parent,
                    "Replace " + target.getFileName() + "?", "Export Diagram", JOptionPane.YES_NO_OPTION)
                    != JOptionPane.YES_OPTION) {
                return;
            }
            preferences.put("exportDirectory", target.toAbsolutePath().getParent().toString());
            // Keep Swing scene painting on EDT; file encoding and replacement happen on this worker.
            new SwingWorker<Void, Void>() {
                protected Void doInBackground() throws Exception {
                    DiagramExporter.export(scene, target, format, scale);
                    return null;
                }

                protected void done() {
                    try {
                        get();
                        JOptionPane.showMessageDialog(parent, "Exported to " + target.toAbsolutePath(), "Export Diagram", JOptionPane.INFORMATION_MESSAGE);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    } catch (ExecutionException ex) {
                        error(parent, ex.getCause());
                    }
                }
            }.execute();
        } catch (Exception ex) {
            error(parent, ex);
        }
    }

    private static void error(Frame parent, Throwable ex) {
        JOptionPane.showMessageDialog(parent, "Export failed: " + ex.getMessage(), "Export Diagram", JOptionPane.ERROR_MESSAGE);
    }
}
