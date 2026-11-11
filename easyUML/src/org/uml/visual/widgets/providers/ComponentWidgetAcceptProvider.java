package org.uml.visual.widgets.providers;

import java.awt.Graphics2D;
import javax.swing.Icon;
import java.awt.Point;
import java.awt.datatransfer.Transferable;
import javax.swing.JComponent;
import org.netbeans.api.visual.action.AcceptProvider;
import org.netbeans.api.visual.action.ConnectorState;
import org.netbeans.api.visual.widget.Widget;
import org.openide.nodes.NodeTransfer;
import org.openide.util.Exceptions;
import org.openide.util.ImageUtilities;
import org.uml.model.relations.ImplementsRelation;
import org.uml.model.relations.RelationBase;
import org.uml.visual.dialogs.ConnectRelationPanel;
import org.uml.visual.palette.PaletteItemNode;
import org.uml.visual.widgets.components.ComponentWidgetBase;
import org.uml.visual.widgets.components.EnumWidget;
import org.uml.visual.widgets.components.InterfaceWidget;

/**
 *
 * @author NUGS
 */
public class ComponentWidgetAcceptProvider implements AcceptProvider {

    @Override
    public ConnectorState isAcceptable(Widget widget, Point point, Transferable t) {
        PaletteItemNode pin = (PaletteItemNode) NodeTransfer.node(t, NodeTransfer.DND_COPY_OR_MOVE);
        Class<?> droppedClass = pin.getPaletteItem().getDropClass();
        Icon dragIcon = ImageUtilities.loadImageIcon(pin.getPaletteItem().getIcon(), false);
        JComponent view = widget.getScene().getView();
        view.paintImmediately(view.getVisibleRect());
        Graphics2D g2 = (Graphics2D) view.getGraphics();
        if (g2 != null) {
            try {
                if (dragIcon != null) {
                    dragIcon.paintIcon(view, g2, widget.getLocation().x + point.x, widget.getLocation().y + point.y);
                }
            } finally {
                g2.dispose();
            }
        }

        return canAccept(widget, droppedClass) ? ConnectorState.ACCEPT : ConnectorState.REJECT;
    }

    @Override
    public void accept(Widget widget, Point point, Transferable t) {
        try {
            PaletteItemNode pin = (PaletteItemNode) NodeTransfer.node(t, NodeTransfer.DND_COPY_OR_MOVE);
            Class<?> droppedClass = pin.getPaletteItem().getDropClass();
            Class<? extends RelationBase> relationClass = droppedClass.asSubclass(RelationBase.class);
            RelationBase relation = relationClass.getDeclaredConstructor().newInstance();
            ComponentWidgetBase componentWidget = (ComponentWidgetBase) widget;
            relation.setSource(componentWidget.getComponent());

            if (!(relation instanceof ImplementsRelation && !(widget instanceof InterfaceWidget))) {
                ConnectRelationPanel panel = new ConnectRelationPanel(componentWidget.getClassDiagramScene(), componentWidget, relation);
                panel.openRelationDialog();
            }

            widget.getScene().repaint();
        } catch (ReflectiveOperationException ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    private boolean canAccept(Widget widget, Class<?> droppedClass) {
        if (RelationBase.class.isAssignableFrom(droppedClass)) {
            if (widget instanceof EnumWidget) {
                return false;
            } else if (widget instanceof InterfaceWidget && !droppedClass.equals(ImplementsRelation.class)) {
                return false;
            } else {
                return true;
            }
        } else {
            return false;
        }
    }
}
