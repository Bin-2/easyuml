package org.uml.filetype.cdg;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.util.Collection;
import javax.swing.JOptionPane;
import org.dom4j.Document;
import org.dom4j.Element;
import org.netbeans.api.actions.Openable;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.MIMEResolver;
import org.openide.loaders.DataNode;
import org.openide.loaders.DataObject;
import org.openide.loaders.DataObjectExistsException;
import org.openide.loaders.MultiDataObject;
import org.openide.loaders.MultiFileLoader;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.util.Lookup;
import org.openide.util.LookupEvent;
import org.openide.util.LookupListener;
import org.openide.util.NbBundle.Messages;
import org.openide.util.lookup.AbstractLookup;
import org.openide.util.lookup.InstanceContent;
import org.openide.util.lookup.ProxyLookup;
import org.openide.windows.WindowManager;
import org.uml.filetype.cdg.renaming.MyClassDiagramRenameTable;
import org.uml.model.ClassDiagram;
import org.uml.visual.UMLTopComponent;
import org.uml.xmlDeserialization.ClassDiagramDeserializer;

@Messages({
    "LBL_ClassDiagram_LOADER=Files of ClassDiagram"
})
@MIMEResolver.ExtensionRegistration(
        displayName = "#LBL_ClassDiagram_LOADER",
        mimeType = "text/x-cdg",
        extension = {"cdg", "CDG"},
        position = 400
)
@DataObject.Registration(
        mimeType = "text/x-cdg",
        iconBase = "org/uml/filetype/cdg/classDiagramIcon.png",
        displayName = "#LBL_ClassDiagram_LOADER",
        position = 300
)
@ActionReferences({
    @ActionReference(
            path = "Loaders/text/x-cdg/Actions",
            id = @ActionID(category = "System", id = "org.openide.actions.OpenAction"),
            position = 100,
            separatorAfter = 200
    ),
    @ActionReference(
            path = "Loaders/text/x-cdg/Actions",
            id = @ActionID(category = "Edit", id = "org.openide.actions.CutAction"),
            position = 300
    ),
    @ActionReference(
            path = "Loaders/text/x-cdg/Actions",
            id = @ActionID(category = "Edit", id = "org.openide.actions.CopyAction"),
            position = 400,
            separatorAfter = 500
    ),
    @ActionReference(
            path = "Loaders/text/x-cdg/Actions",
            id = @ActionID(category = "Edit", id = "org.openide.actions.DeleteAction"),
            position = 600
    ),
    @ActionReference(
            path = "Loaders/text/x-cdg/Actions",
            id = @ActionID(category = "System", id = "org.openide.actions.RenameAction"),
            position = 700,
            separatorAfter = 800
    ),
//    @ActionReference(
//            path = "Loaders/text/x-cdg/Actions",
//            id = @ActionID(category = "System", id = "org.openide.actions.SaveAsTemplateAction"),
//            position = 900,
//            separatorAfter = 1000
//    ),
    @ActionReference(
            path = "Loaders/text/x-cdg/Actions",
            id = @ActionID(category = "System", id = "org.openide.actions.FileSystemAction"),
            position = 1100,
            separatorAfter = 1200
    ),
    @ActionReference(
            path = "Loaders/text/x-cdg/Actions",
            id = @ActionID(category = "System", id = "org.openide.actions.ToolsAction"),
            position = 1300
    ),
    @ActionReference(
            path = "Loaders/text/x-cdg/Actions",
            id = @ActionID(category = "System", id = "org.openide.actions.PropertiesAction"),
            position = 1400
    )
})
public class ClassDiagramDataObject extends MultiDataObject implements Openable, LookupListener {

    private ClassDiagram classDiagram;
    private UMLTopComponent umlTopComponent;
    private final MyClassDiagramRenameTable classDiagramRenameTable;

    private final InstanceContent content = new InstanceContent();
    private final AbstractLookup aLookup = new AbstractLookup(content);
    private Lookup.Result<UMLTopComponent.Save> savable;
    private UMLTopComponent.Save oldSave;

    public ClassDiagramDataObject(FileObject fo, MultiFileLoader loader) throws DataObjectExistsException, IOException {
        super(fo, loader);
        // Load here for explorer (access through lookup)
        classDiagram = readFromFile(getPrimaryFile());
        content.add(classDiagram);

        // Create rename tables
        classDiagramRenameTable = new MyClassDiagramRenameTable(classDiagram);

        this.addPropertyChangeListener(new PropertyChangeListener() {
            @Override
            public void propertyChange(final PropertyChangeEvent evt) {
                if (evt.getPropertyName().equals("name")) {
                    WindowManager.getDefault().invokeWhenUIReady(new Runnable() {
                        @Override
                        public void run() {
                            if (umlTopComponent != null) {
                                umlTopComponent.setName(getPrimaryFile().getNameExt());
                            }
                        }
                    });
                }
            }
        });
    }

    @Override
    protected int associateLookup() {
        return 1;
    }

    @Override
    protected Node createNodeDelegate() {
        return new DataNode(this, Children.LEAF, new ProxyLookup(getLookup(), aLookup));
    }

    public ClassDiagram getClassDiagram() {
        return classDiagram;
    }

    public UMLTopComponent getUmlTopComponent() {
        return umlTopComponent;
    }

    public MyClassDiagramRenameTable getRenames() {
        return classDiagramRenameTable;
    }

    @Override
    public void open() {
        // Needs to read here, to reload on each opening
        if (umlTopComponent == null || !umlTopComponent.isOpened()) {
            ClassDiagram loaded;
            try {
                loaded = readFromFile(getPrimaryFile());
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(null, ex.getMessage(), "Cannot open diagram", JOptionPane.ERROR_MESSAGE);
                return;
            }
            content.remove(classDiagram);
            classDiagram = loaded;
            content.add(classDiagram);

            classDiagramRenameTable.updateClassDiagram(classDiagram);

            if (classDiagram == null) {
                classDiagram = new ClassDiagram();
                classDiagram.setName(getPrimaryFile().getName());
            }
            umlTopComponent = new UMLTopComponent(classDiagram, getPrimaryFile().getNameExt());
            umlTopComponent.setFileObject(getPrimaryFile());
            umlTopComponent.open();
//            // For Navigator panel
//            content.add(umlTopComponent.getClassDiagramScene());
            savable = umlTopComponent.getLookup().lookupResult(UMLTopComponent.Save.class);
            savable.addLookupListener(this);
        }
        umlTopComponent.requestActive();
    }

    public void notifyDispose() {
        WindowManager.getDefault().invokeWhenUIReady(new Runnable() {
            @Override
            public void run() {
                if (umlTopComponent != null && umlTopComponent.isOpened()) {
                    if (!umlTopComponent.close()) {
                        return;
                    }
                }
                dispose();
            }
        });
    }

    private ClassDiagram readFromFile(FileObject fileObject) throws IOException {
        ClassDiagram classDiag = new ClassDiagram();
        classDiag.setName(fileObject.getName());
        try {
            if (fileObject.getSize() > 0) {
                try (java.io.BufferedInputStream input = new java.io.BufferedInputStream(fileObject.getInputStream())) {
                    input.mark(2);
                    int first = input.read();
                    int second = input.read();
                    input.reset();
                    // Historical wizard wrote a serialized empty diagram which the old reader ignored.
                    // Preserve that empty-diagram behavior without deserializing untrusted Java objects.
                    if (first == 0xac && second == 0xed) {
                        return classDiag;
                    }
                    Document document = org.uml.xmlDeserialization.DiagramXml.read(input);
                    Element root = document.getRootElement();
                    if (!"ClassDiagram".equals(root.getName())) {
                        throw new IOException("Expected ClassDiagram root");
                    }
                    new ClassDiagramDeserializer(classDiag).deserialize(root);
                }
            }
        } catch (IOException | RuntimeException ex) {
            throw new IOException("Malformed or unreadable diagram: " + fileObject.getNameExt(),
                    ex instanceof IOException ? (IOException) ex : new IOException(ex));
        }
        return classDiag;
    }

    @Override
    protected void handleDelete() throws IOException {
        super.handleDelete();
        if (umlTopComponent != null) {
            umlTopComponent.close();
        }
    }

    @Override
    public void dispose() {
        super.dispose();
    }

    @Override
    public void resultChanged(LookupEvent ev) {
        Lookup.Result source = (Lookup.Result) ev.getSource();
        Collection instances = source.allInstances();
        if (!instances.isEmpty()) {
            for (Object instance : instances) {
                if (instance instanceof UMLTopComponent.Save && oldSave == null) {
                    oldSave = (UMLTopComponent.Save) instance;
                    content.add(instance);
                }
                break;
            }
        } else {
            if (oldSave != null) {
                content.remove(oldSave);
                oldSave = null;
            }
        }
    }
}
