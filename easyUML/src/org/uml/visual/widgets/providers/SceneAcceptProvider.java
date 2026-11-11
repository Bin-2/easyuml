package org.uml.visual.widgets.providers;

import java.awt.Graphics2D;
import javax.swing.Icon;
import java.awt.Point;
import java.awt.datatransfer.Transferable;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import org.netbeans.api.visual.action.AcceptProvider;
import org.netbeans.api.visual.action.ConnectorState;
import org.netbeans.api.visual.widget.Widget;
import org.openide.nodes.Node;
import org.openide.nodes.NodeTransfer;
import org.openide.util.Exceptions;
import org.openide.util.ImageUtilities;
import org.uml.model.components.ComponentBase;
import org.uml.model.relations.RelationBase;
import org.uml.visual.dialogs.ConnectRelationPanel;
import org.uml.visual.widgets.ClassDiagramScene;
import org.uml.visual.palette.*;

/**
 *
 * @author Uros
 */
public class SceneAcceptProvider implements AcceptProvider {

    // TODO make efficient drawing of drag icons
    @Override
    public ConnectorState isAcceptable(Widget widget, Point point, Transferable t) {
        PaletteItemNode pin = (PaletteItemNode) NodeTransfer.node(t, NodeTransfer.DND_COPY_OR_MOVE);
        Icon dragIcon = ImageUtilities.loadImageIcon(pin.getPaletteItem().getIcon(), false);
        JComponent view = widget.getScene().getView();
        view.paintImmediately(view.getVisibleRect());
        Graphics2D g2 = (Graphics2D) view.getGraphics();
        if (g2 != null) {
            try {
                if (dragIcon != null) {
                    dragIcon.paintIcon(view, g2, point.x, point.y);
                }
            } finally {
                g2.dispose();
            }
        }

        return ConnectorState.ACCEPT;
    }

    @Override
    public void accept(Widget widget, Point point, Transferable t) {
        ClassDiagramScene classDiagramScene = (ClassDiagramScene) widget;
        Node node = NodeTransfer.node(t, NodeTransfer.DND_COPY_OR_MOVE);
        PaletteItemNode pin = (PaletteItemNode) node;
        Class<?> droppedComponentClass = pin.getPaletteItem().getDropClass();
        try {
            if (ComponentBase.class.isAssignableFrom(droppedComponentClass)) {
                Constructor<?> cons = droppedComponentClass.getConstructor();
                Object cobj = cons.newInstance();
                ComponentBase component = (ComponentBase) cobj;

                Widget w = classDiagramScene.addNode(component);
                w.setPreferredLocation(point);
                component.setLocation(w.getLocation());
//                Rectangle bounds = new Rectangle(point.x, point.y, w.getMinimumSize().width, w.getMinimumSize().height);
//                w.setPreferredBounds(bounds);
//                w.resolveBounds(point, bounds);
//                component.setBounds(w.getPreferredBounds());
                classDiagramScene.setFocusedObjectAndToggleListeners(component);

                // TODO open inplace editor - throws NPE because nameLabel does not have its bounds set
//                WidgetAction editorAction = ((ComponentWidgetBase) w).getNameEditorAction();
//                EditorController ec = ActionFactory.getInplaceEditorController(editorAction);
//                ec.openEditor(((ComponentWidgetBase)w).getNameLabel());
//                classDiagramScene.getView().addMouseListener(new CloseInplaceEditorOnClickAdapter(editorAction));
            } else if (RelationBase.class.isAssignableFrom(droppedComponentClass)) {
                Object cobj = droppedComponentClass.getDeclaredConstructor().newInstance();
                RelationBase relation = (RelationBase) cobj;
                ConnectRelationPanel panel = new ConnectRelationPanel(classDiagramScene, relation);
                panel.openRelationDialog();
            }
        } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException | RuntimeException ex) {
            JOptionPane.showMessageDialog(null, "SceneAcceptProvider exception! " + ex.getMessage());
            Exceptions.printStackTrace(ex);
        }

        classDiagramScene.getUmlTopComponent().requestActive();
        classDiagramScene.validate();
    }
}
