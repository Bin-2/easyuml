package org.uml.visual.widgets.actions;

import org.netbeans.api.visual.action.TextFieldInplaceEditor;
import org.netbeans.api.visual.widget.LabelWidget;
import org.netbeans.api.visual.widget.Widget;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.uml.model.relations.RelationBase;
import org.uml.visual.widgets.ClassDiagramScene;

/**
 *
 * @author Uros
 */
public class RelationLabelTextFieldEditorAction implements TextFieldInplaceEditor {

    RelationBase relation;

    public RelationLabelTextFieldEditorAction(RelationBase relationComponent) {
        this.relation = relationComponent;
    }

    @Override
    public boolean isEnabled(Widget widget) {
        return true;
    }

    @Override
    public String getText(Widget widget) {
        return relation.getName();
    }

    @Override
    public void setText(Widget widget, String string) {
        ClassDiagramScene scene = (ClassDiagramScene) widget.getScene();
        if (scene.getClassDiagram().renameRelation(relation, string)) {
            ((LabelWidget) widget).setLabel(string);
        } else if (scene.isEdge(relation)) {
            DialogDisplayer.getDefault().notifyLater(new NotifyDescriptor.Message(
                    "Relation already exists!", NotifyDescriptor.WARNING_MESSAGE));
        }
    }
}
