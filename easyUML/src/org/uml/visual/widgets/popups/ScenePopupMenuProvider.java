package org.uml.visual.widgets.popups;

import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.EnumMap;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import org.netbeans.api.visual.action.ActionFactory;
import org.netbeans.api.visual.action.PopupMenuProvider;
import org.netbeans.api.visual.action.WidgetAction;
import org.netbeans.api.visual.widget.Widget;
import org.openide.windows.WindowManager;
import org.uml.jung.JUNGEngine;
import org.uml.model.components.ClassComponent;
import org.uml.model.components.EnumComponent;
import org.uml.model.components.InterfaceComponent;
import org.uml.visual.dialogs.ConnectRelationPanel;
import org.uml.visual.widgets.ClassDiagramScene;
import org.uml.visual.widgets.ClassDiagramScene.RelationshipView;
import org.uml.visual.widgets.actions.ComponentNameEditor;
import org.uml.visual.widgets.anchors.RelationshipRouter;
import org.uml.visual.widgets.components.ClassWidget;
import org.uml.visual.widgets.components.EnumWidget;
import org.uml.visual.widgets.components.InterfaceWidget;
import org.uml.visual.widgets.providers.CloseInplaceEditorOnClickAdapter;

/**
 *
 * @author hrza
 */
public class ScenePopupMenuProvider implements PopupMenuProvider {

    private final ClassDiagramScene scene;
    private final JPopupMenu sceneMenu;
    private JMenuItem miCreateClass;
    private JMenuItem miCreateInterface;
    private JMenuItem miCreateEnum;
    private JMenuItem miCreateRelationship;
//    private JMenuItem miGenerateCode;
    private JMenuItem miExportDiagram;
    private JMenuItem miApplyJUNGLayout;
    private JMenu mVisualOptions;
    private JCheckBoxMenuItem miShowIcons;
    private JCheckBoxMenuItem miShowMembers;
    private JCheckBoxMenuItem miShowSimpleTypeNames;
    private final EnumMap<RelationshipView, JRadioButtonMenuItem> relationshipItems
            = new EnumMap<>(RelationshipView.class);
    private final JRadioButtonMenuItem miBlueGray = new JRadioButtonMenuItem("Blue-gray");
    private final JRadioButtonMenuItem miSand = new JRadioButtonMenuItem("Sand-red");
    private JMenu mColorTheme;
    private Point popupPoint;

    public ScenePopupMenuProvider(ClassDiagramScene scene) {
        this.scene = scene;
        sceneMenu = new JPopupMenu("Diagram menu!");
        generateMenu();
    }

    @Override
    public JPopupMenu getPopupMenu(Widget widget, Point point) {
        popupPoint = point;
        updateMenu();
        return sceneMenu;
    }

    public final void generateMenu() {

        miCreateClass = new JMenuItem("Add Class");
        miCreateClass.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // add new class component and widget to scene
                // here we should just add new class component to model, and the scene should be updated elsewhere
                ClassWidget widget = (ClassWidget) scene.addNode(new ClassComponent());
                widget.setPreferredLocation(popupPoint);
                widget.getComponent().setLocation(popupPoint);
                scene.validate();

                // Temp renamer
                WidgetAction editorAction = ActionFactory.createInplaceEditorAction(new ComponentNameEditor(widget));
                ActionFactory.getInplaceEditorController(editorAction).openEditor(widget.getNameLabel());
                scene.getView().addMouseListener(new CloseInplaceEditorOnClickAdapter(editorAction));
            }
        });
        miCreateInterface = new JMenuItem("Add Interface");
        miCreateInterface.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                InterfaceWidget widget = (InterfaceWidget) scene.addNode(new InterfaceComponent());
                widget.setPreferredLocation(popupPoint);
                widget.getComponent().setLocation(popupPoint);
                scene.validate();

                // Temp renamer
                WidgetAction editorAction = ActionFactory.createInplaceEditorAction(new ComponentNameEditor(widget));
                ActionFactory.getInplaceEditorController(editorAction).openEditor(widget.getNameLabel());
                scene.getView().addMouseListener(new CloseInplaceEditorOnClickAdapter(editorAction));
            }
        });
        miCreateEnum = new JMenuItem("Add Enum");
        miCreateEnum.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                EnumWidget widget = (EnumWidget) scene.addNode(new EnumComponent());
                widget.setPreferredLocation(popupPoint);
                widget.getComponent().setLocation(popupPoint);
                scene.validate();

                // Temp renamer
                WidgetAction editorAction = ActionFactory.createInplaceEditorAction(new ComponentNameEditor(widget));
                ActionFactory.getInplaceEditorController(editorAction).openEditor(widget.getNameLabel());
                scene.getView().addMouseListener(new CloseInplaceEditorOnClickAdapter(editorAction));
            }
        });

        miCreateRelationship = new JMenuItem("Add Relationship");
        miCreateRelationship.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                ConnectRelationPanel panel = new ConnectRelationPanel(scene);
                panel.openRelationDialog();
            }
        });

//        miGenerateCode = new JMenuItem("Generate code");
//        miGenerateCode.addActionListener(new ActionListener() {
//            @Override
//            public void actionPerformed(ActionEvent e) {
//                GenerateCodeDialog dialog = new GenerateCodeDialog(scene.getClassDiagram());
//                dialog.setLocationRelativeTo(WindowManager.getDefault().getMainWindow());
//                dialog.setVisible(true);
//            }
//        });
        miExportDiagram = new JMenuItem("Export Diagram...");
        miExportDiagram.addActionListener(e -> org.uml.visual.dialogs.ExportDiagramDialog.show(scene));

        miApplyJUNGLayout = new JMenuItem("Arrange Diagram");
        miApplyJUNGLayout.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JUNGEngine je = new JUNGEngine(scene);
                je.applyJUNGLayout();
            }
        });

        mVisualOptions = new JMenu("Visual options");

        miShowIcons = new JCheckBoxMenuItem("Show icons");
        miShowIcons.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                scene.setShowIcons(!scene.isShowIcons());
                WindowManager.getDefault().findTopComponent("properties").repaint();
            }
        });
        mVisualOptions.add(miShowIcons);

        miShowMembers = new JCheckBoxMenuItem("Show members");
        miShowMembers.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                scene.setShowMembers(!scene.isShowMembers());
                WindowManager.getDefault().findTopComponent("properties").repaint();
            }
        });
        mVisualOptions.add(miShowMembers);

        miShowSimpleTypeNames = new JCheckBoxMenuItem("Show simple class names");
        miShowSimpleTypeNames.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                scene.setShowSimpleTypes(!scene.isShowSimpleTypes());
                WindowManager.getDefault().findTopComponent("properties").repaint();
            }
        });
        mVisualOptions.add(miShowSimpleTypeNames);
        mVisualOptions.add(createRelationshipMenu());

        ButtonGroup themeButtonGroup = new ButtonGroup();
        mColorTheme = new JMenu("Color theme");
        miBlueGray.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                scene.setColorTheme("blue-gray");
                miBlueGray.setSelected(true);
            }
        });
        themeButtonGroup.add(miBlueGray);
        mColorTheme.add(miBlueGray);
        miSand.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                scene.setColorTheme("sand-red");
                miSand.setSelected(true);
            }
        });
        themeButtonGroup.add(miSand);
        mColorTheme.add(miSand);

        sceneMenu.add(miCreateClass);

        sceneMenu.add(miCreateInterface);

        sceneMenu.add(miCreateEnum);

        sceneMenu.addSeparator();

        sceneMenu.add(miCreateRelationship);

        sceneMenu.addSeparator();

//        sceneMenu.add(miGenerateCode);
        sceneMenu.add(miExportDiagram);

        sceneMenu.addSeparator();

        sceneMenu.add(miApplyJUNGLayout);

        JMenuItem routeRelationships = new JMenuItem("Route Relationships");
        routeRelationships.addActionListener(e -> RelationshipRouter.route(scene));
        sceneMenu.add(routeRelationships);

        sceneMenu.add(mVisualOptions);

        sceneMenu.add(mColorTheme);
    }

    private JMenu createRelationshipMenu() {
        JMenu menu = new JMenu("Relationships");
        ButtonGroup group = new ButtonGroup();
        addRelationshipOption(menu, group, "All Relationships", RelationshipView.ALL);
        addRelationshipOption(menu, group, "Inheritance and Implementation", RelationshipView.HIERARCHY);
        addRelationshipOption(menu, group, "Selection Only", RelationshipView.SELECTION);
        return menu;
    }

    private void addRelationshipOption(JMenu menu, ButtonGroup group, String label, RelationshipView view) {
        JRadioButtonMenuItem item = new JRadioButtonMenuItem(label);
        item.addActionListener(e -> scene.setRelationshipView(view));
        group.add(item);
        menu.add(item);
        relationshipItems.put(view, item);
    }

    private void updateMenu() {
        miShowIcons.setSelected(scene.isShowIcons());
        miShowMembers.setSelected(scene.isShowMembers());
        miShowSimpleTypeNames.setSelected(scene.isShowSimpleTypes());
        relationshipItems.get(scene.getRelationshipView()).setSelected(true);
        switch (scene.getColorTheme().getName()) {
            case "blue-gray":
                miBlueGray.setSelected(true);
                break;
            case "sand-red":
                miSand.setSelected(true);
                break;
        }
    }

}
