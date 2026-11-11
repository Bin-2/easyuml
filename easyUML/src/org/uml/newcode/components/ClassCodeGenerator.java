package org.uml.newcode.components;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EmptyTypeDeclaration;
import com.github.javaparser.ast.body.ModifierSet;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import org.uml.filetype.cdg.renaming.MyClassDiagramRenameTable;
import org.uml.filetype.cdg.renaming.MyMembersRenameTable;
import org.uml.model.Visibility;
import org.uml.model.components.ClassComponent;
import org.uml.model.components.ComponentBase;
import org.uml.model.relations.ImplementsRelation;
import org.uml.model.relations.IsRelation;
import org.uml.model.relations.RelationBase;
import org.uml.newcode.members.ConstructorCodeGenerator;
import org.uml.newcode.members.FieldCodeGenerator;
import org.uml.newcode.members.MethodCodeGenerator;

/**
 * Code generator for classes, produces one .java file.
 *
 * @author Boris Perović
 */
public class ClassCodeGenerator extends ComponentCodeGeneratorBase<ClassComponent> {

    private static ClassCodeGenerator instance;

    public static ClassCodeGenerator getInstance() {
        if (instance == null) {
            instance = new ClassCodeGenerator();
        }
        return instance;
    }

    @Override
    protected String generateCode(ClassComponent component) {
        CompilationUnit cu = new CompilationUnit();
        cu.setTypes(new LinkedList<TypeDeclaration>());
        if (!component.getParentPackage().isEmpty()) {
            cu.setPackage(new PackageDeclaration(new NameExpr(component.getParentPackage())));
        }
        createSkeleton(component, cu);
        FieldCodeGenerator.createFields(component, cu);
        ConstructorCodeGenerator.createConstructors(component, cu);
        MethodCodeGenerator.createMethods(component, cu);

        return cu.toString();
    }

    private static void createSkeleton(ClassComponent component, CompilationUnit cu) {
        ClassOrInterfaceDeclaration declaration = new ClassOrInterfaceDeclaration();
        declaration.setName(component.getName());
        switch (component.getVisibility()) {
            case PUBLIC:
                declaration.setModifiers(ModifierSet.addModifier(declaration.getModifiers(), ModifierSet.PUBLIC));
                break;
            case PROTECTED:
                declaration.setModifiers(ModifierSet.addModifier(declaration.getModifiers(), ModifierSet.PROTECTED));
                break;
            case PRIVATE:
                declaration.setModifiers(ModifierSet.addModifier(declaration.getModifiers(), ModifierSet.PRIVATE));
                break;
        }
        if (component.isAbstract()) {
            declaration.setModifiers(ModifierSet.addModifier(declaration.getModifiers(), ModifierSet.ABSTRACT));
        }
        if (component.isStatic()) {
            declaration.setModifiers(ModifierSet.addModifier(declaration.getModifiers(), ModifierSet.STATIC));
        }
        if (component.isFinal()) {
            declaration.setModifiers(ModifierSet.addModifier(declaration.getModifiers(), ModifierSet.FINAL));
        }

        ComponentBase extendedClass = getExtendedClass(component);
        if (extendedClass != null) {
            List<ClassOrInterfaceType> extended = new LinkedList<>();
            extended.add(new ClassOrInterfaceType(extendedClass.getName()));
            declaration.setExtends(extended);
        }
        List<ComponentBase> implementedInterfaces = getImplementedInterfaces(component);
        if (!implementedInterfaces.isEmpty()) {
            List<ClassOrInterfaceType> implemented = new LinkedList<>();
            for (ComponentBase implementedComponent : implementedInterfaces) {
                implemented.add(new ClassOrInterfaceType(implementedComponent.getName()));
            }
            declaration.setImplements(implemented);
        }

        declaration.setMembers(new LinkedList<BodyDeclaration>());

        cu.getTypes().add(declaration);
    }

    private static ComponentBase getExtendedClass(ClassComponent component) {
        for (RelationBase relation : component.getParentDiagram().getRelations()) {
            if (relation instanceof IsRelation && relation.getSource() == component) {
                return relation.getTarget();
            }
        }
        return null;
    }

    private static List<ComponentBase> getImplementedInterfaces(ClassComponent component) {
        List<ComponentBase> implemented = new LinkedList<>();
        for (RelationBase relation : component.getParentDiagram().getRelations()) {
            if (relation instanceof ImplementsRelation && relation.getSource() == component) {
                implemented.add(relation.getTarget());
            }
        }
        return implemented;
    }

    @Override
    protected String updateCode(ClassComponent component, MyClassDiagramRenameTable renames, CompilationUnit cu) throws IOException {
        String original = originalSignature(component, renames);
        String sourcePackage = cu.getPackage() == null ? "" : cu.getPackage().getName().toString();
        ClassOrInterfaceDeclaration declaration = findClass(cu.getTypes(), sourcePackage, original, component.getName());
        String targetPackage = component.getParentPackage();
        if (!targetPackage.equals(sourcePackage)) {
            if (cu.getTypes().stream().anyMatch(type -> type != declaration && !(type instanceof EmptyTypeDeclaration))) {
                throw new IOException("Cannot move " + original + " to another package from a file with other top-level types");
            }
            cu.setPackage(targetPackage.isEmpty() ? null : new PackageDeclaration(new NameExpr(targetPackage)));
        }
        updateSkeleton(component, declaration);
        MyMembersRenameTable memberRenames = renames.getComponentRenames().getMembersRenameTable(component);
        List<BodyDeclaration> members = declaration.getMembers();
        FieldCodeGenerator.updateFields(component, memberRenames, members);
        ConstructorCodeGenerator.updateConstructors(component, memberRenames, members);
        MethodCodeGenerator.updateMethods(component, memberRenames, members);

        return cu.toString();
    }

    private static ClassOrInterfaceDeclaration findClass(List<TypeDeclaration> types, String sourcePackage,
            String original, String targetName) throws IOException {
        String prefix = sourcePackage.isEmpty() ? "" : sourcePackage + ".";
        ClassOrInterfaceDeclaration match = null;
        if (types != null) {
            for (TypeDeclaration type : types) {
                if (type instanceof EmptyTypeDeclaration) {
                    continue;
                }
                String name = type.getName();
                if (original.equals(prefix + name)) {
                    if (!(type instanceof ClassOrInterfaceDeclaration)
                            || ((ClassOrInterfaceDeclaration) type).isInterface() || match != null) {
                        throw new IOException("Expected exactly one top-level class declaration for " + original);
                    }
                    match = (ClassOrInterfaceDeclaration) type;
                } else if (targetName.equals(name)) {
                    throw new IOException("Conflicting top-level declaration " + prefix + name + " while updating " + original);
                }
            }
        }
        if (match == null) {
            throw new IOException("No top-level class declaration found for " + original);
        }
        return match;
    }

    private static void updateSkeleton(ClassComponent component, ClassOrInterfaceDeclaration declaration) {
        declaration.setName(component.getName());
        updateVisibility(declaration, component.getVisibility());

        if (component.isAbstract()) {
            declaration.setModifiers(ModifierSet.addModifier(declaration.getModifiers(), ModifierSet.ABSTRACT));
        } else {
            declaration.setModifiers(ModifierSet.removeModifier(declaration.getModifiers(), ModifierSet.ABSTRACT));
        }
        if (component.isFinal()) {
            declaration.setModifiers(ModifierSet.addModifier(declaration.getModifiers(), ModifierSet.FINAL));
        } else {
            declaration.setModifiers(ModifierSet.removeModifier(declaration.getModifiers(), ModifierSet.FINAL));
        }
        if (component.isStatic()) {
            declaration.setModifiers(ModifierSet.addModifier(declaration.getModifiers(), ModifierSet.STATIC));
        } else {
            declaration.setModifiers(ModifierSet.removeModifier(declaration.getModifiers(), ModifierSet.STATIC));
        }

        ComponentBase extendedClass = getExtendedClass(component);
        if (extendedClass != null) {
            List<ClassOrInterfaceType> extended = new LinkedList<>();
            extended.add(new ClassOrInterfaceType(extendedClass.getName()));
            declaration.setExtends(extended);
        }
        List<ComponentBase> implementedInterfaces = getImplementedInterfaces(component);
        if (!implementedInterfaces.isEmpty()) {
            List<ClassOrInterfaceType> implemented = new LinkedList<>();
            for (ComponentBase implementedComponent : implementedInterfaces) {
                implemented.add(new ClassOrInterfaceType(implementedComponent.getName()));
            }
            declaration.setImplements(implemented);
        }
    }

    private static void updateVisibility(TypeDeclaration declaration, Visibility visibility) {
        int modifiers = declaration.getModifiers();
        modifiers = ModifierSet.removeModifier(modifiers, ModifierSet.PUBLIC);
        modifiers = ModifierSet.removeModifier(modifiers, ModifierSet.PROTECTED);
        modifiers = ModifierSet.removeModifier(modifiers, ModifierSet.PRIVATE);

        switch (visibility) {
            case PUBLIC:
                declaration.setModifiers(ModifierSet.addModifier(modifiers, ModifierSet.PUBLIC));
                break;
            case PROTECTED:
                declaration.setModifiers(ModifierSet.addModifier(modifiers, ModifierSet.PROTECTED));
                break;
            case PRIVATE:
                declaration.setModifiers(ModifierSet.addModifier(modifiers, ModifierSet.PRIVATE));
                break;
        }
    }
}
