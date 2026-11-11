package org.uml.reveng;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseException;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.ModifierSet;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.body.VariableDeclaratorId;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.ReferenceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.WildcardType;
import java.awt.Point;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.uml.model.ClassDiagram;
import org.uml.model.Visibility;
import org.uml.model.components.ClassComponent;
import org.uml.model.components.ComponentBase;
import org.uml.model.components.EnumComponent;
import org.uml.model.components.InterfaceComponent;
import org.uml.model.members.Constructor;
import org.uml.model.members.Field;
import org.uml.model.members.Literal;
import org.uml.model.members.MemberBase;
import org.uml.model.members.Method;
import org.uml.model.members.MethodArgument;
import org.uml.model.relations.AggregationRelation;
import org.uml.model.relations.CardinalityEnum;
import org.uml.model.relations.HasBaseRelation;
import org.uml.model.relations.ImplementsRelation;
import org.uml.model.relations.IsRelation;
import org.uml.model.relations.RelationBase;
import org.uml.model.relations.UseRelation;

/**
 *
 * @author Boris Perović
 */
public class ReverseEngineer {

    @SuppressWarnings("unchecked")
    public static ClassDiagram createClassDiagramFromFiles(Collection<File> files, String diagramName) throws IOException {
        if (files.isEmpty()) {
            throw new IOException("No Java source files were selected for reverse engineering.");
        }
        ClassDiagram classDiagram = new ClassDiagram();
        classDiagram.setName(diagramName);
        Map<String, ComponentBase> componentsByName = new HashMap<>();

        // First pass - generate components
        for (File file : files) {
            List<ComponentBase> components = createComponents(file);
            for (ComponentBase component : components) {
                String qualifiedName = component.getSignature();
                // A repeated source name has no unique endpoint in the selected files.
                componentsByName.put(qualifiedName, componentsByName.containsKey(qualifiedName) ? null : component);
                classDiagram.addComponent(component);
            }
        }

        if (classDiagram.getComponents().isEmpty()) {
            throw new IOException("The selected Java sources contain no top-level classes, interfaces, or enums to diagram.");
        }

        // Second pass - generate relationships
        for (File file : files) {
            List<RelationBase> relations = createRelations(file, componentsByName);
            for (RelationBase relation : relations) {
                classDiagram.addRelation(relation);
                // Remove a field from the source which corresponds to the relation
                // Relation fields are exclusively represented by relations in the class diagram
                if (relation instanceof HasBaseRelation) {
                    HasBaseRelation hasRelation = (HasBaseRelation) relation;
                    ComponentBase source = hasRelation.getSource();
                    for (MemberBase member : source.getMembers()) {
                        if (member instanceof Field) {
                            Field field = (Field) member;
                            if (hasRelation.getFieldSignature().equals(field.getSignature())) {
                                source.removeMember(field);
                                // One field per relation
                                break;
                            }
                        }
                    }
                }
            }
        }

        resetNextComponentPosition();
        return classDiagram;
    }

    private static List<ComponentBase> createComponents(File file) throws IOException {
        List<ComponentBase> components = new LinkedList<>();
        try {
            CompilationUnit cu = JavaParser.parse(file, "UTF-8");
            for (TypeDeclaration type : safe(cu.getTypes())) {
                ComponentBase component = null;
                if (type instanceof ClassOrInterfaceDeclaration) {
                    ClassOrInterfaceDeclaration cidecl = (ClassOrInterfaceDeclaration) type;
                    if (!cidecl.isInterface()) {
                        component = createClass(cidecl);
                    } else {
                        component = createInterface(cidecl);
                    }
                } else if (type instanceof EnumDeclaration) {
                    EnumDeclaration edecl = (EnumDeclaration) type;
                    component = createEnum(edecl);
                }
                if (component != null) {
                    components.add(component);
                }
            }
        } catch (ParseException | com.github.javaparser.TokenMgrError | RuntimeException ex) {
            throw new IOException("Cannot reverse engineer " + file + ": invalid or unsupported Java syntax. " + ex.getMessage(), ex);
        }
        return components;
    }

    private static ClassComponent createClass(ClassOrInterfaceDeclaration declaration) {
        ClassComponent clazz = new ClassComponent(declaration.getName());
        clazz.setAbstract(ModifierSet.isAbstract(declaration.getModifiers()));
        clazz.setFinal(ModifierSet.isFinal(declaration.getModifiers()));
        clazz.setStatic(ModifierSet.isStatic(declaration.getModifiers()));
        // TODO fix for inner classes
        clazz.setParentPackage(packageName((CompilationUnit) declaration.getParentNode()));

        for (BodyDeclaration member : safe(declaration.getMembers())) {
            if (member instanceof FieldDeclaration) {
                FieldDeclaration declarationOfFields = (FieldDeclaration) member;
                for (VariableDeclarator variable : declarationOfFields.getVariables()) {
                    clazz.addField(createField(declarationOfFields, variable));
                }
            } else if (member instanceof ConstructorDeclaration) {
                Constructor constructorMember = createConstructor((ConstructorDeclaration) member);
                clazz.addConstructor(constructorMember);
            } else if (member instanceof MethodDeclaration) {
                Method methodMember = createMethod((MethodDeclaration) member);
                clazz.addMethod(methodMember);
            }
        }
        clazz.setLocation(getNextComponentPosition());

        return clazz;
    }

    private static ComponentBase createInterface(ClassOrInterfaceDeclaration declaration) {
        InterfaceComponent interfaze = new InterfaceComponent(declaration.getName());
        interfaze.setStatic(ModifierSet.isStatic(declaration.getModifiers()));

        interfaze.setParentPackage(packageName((CompilationUnit) declaration.getParentNode()));

        for (BodyDeclaration member : safe(declaration.getMembers())) {
            if (member instanceof MethodDeclaration) {
                Method methodMember = createMethod((MethodDeclaration) member);
                interfaze.addMethod(methodMember);
            }
        }
        interfaze.setLocation(getNextComponentPosition());

        return interfaze;
    }

    private static ComponentBase createEnum(EnumDeclaration declaration) {
        EnumComponent enumm = new EnumComponent(declaration.getName());

        enumm.setParentPackage(packageName((CompilationUnit) declaration.getParentNode()));

        for (BodyDeclaration entry : safe(declaration.getEntries())) {
            if (entry instanceof EnumConstantDeclaration) {
                Literal literalMember = createLiteral((EnumConstantDeclaration) entry);
                enumm.addLiteral(literalMember);
            }
        }
        enumm.setLocation(getNextComponentPosition());

        return enumm;
    }

    private static Field createField(FieldDeclaration declaration, VariableDeclarator variable) {
        VariableDeclaratorId id = variable.getId();
        String type = declaration.getType().toStringWithoutComments();
        for (int i = 0; i < id.getArrayCount(); i++) {
            type += "[]";
        }
        Field field = new Field(id.getName(), type, getVisibility(declaration));

        int modifiers = declaration.getModifiers();
        field.setStatic(ModifierSet.isStatic(modifiers));
        field.setFinal(ModifierSet.isFinal(modifiers));
        field.setVolatile(ModifierSet.isVolatile(modifiers));
        field.setTransient(ModifierSet.isTransient(modifiers));

        return field;
    }

    private static Visibility getVisibility(FieldDeclaration declaration) {
        int modifiers = declaration.getModifiers();

        if (ModifierSet.isPublic(modifiers)) {
            return Visibility.PUBLIC;
        } else if (ModifierSet.isProtected(modifiers)) {
            return Visibility.PROTECTED;
        } else if (ModifierSet.isPrivate(modifiers)) {
            return Visibility.PRIVATE;
        } else {
            return Visibility.PACKAGE;
        }
    }

    private static Method createMethod(MethodDeclaration declaration) {
        Method method = new Method(declaration.getName(), declaration.getType().toString());
        method.setVisibility(getVisibility(declaration));

        int modifiers = declaration.getModifiers();
        method.setAbstract(ModifierSet.isAbstract(modifiers));
        method.setStatic(ModifierSet.isStatic(modifiers));
        method.setFinal(ModifierSet.isFinal(modifiers));
        method.setSynchronized(ModifierSet.isSynchronized(modifiers));

        LinkedHashSet<MethodArgument> arguments = method.getArguments();
        for (Parameter parameter : safe(declaration.getParameters())) {
            MethodArgument arg = new MethodArgument(parameter.getType().toString(), parameter.getId().getName());
            arguments.add(arg);
        }

        return method;
    }

    private static Visibility getVisibility(MethodDeclaration declaration) {
        int modifiers = declaration.getModifiers();
        if (ModifierSet.isPublic(modifiers)) {
            return Visibility.PUBLIC;
        } else if (ModifierSet.isProtected(modifiers)) {
            return Visibility.PROTECTED;
        } else if (ModifierSet.isPrivate(modifiers)) {
            return Visibility.PRIVATE;
        } else {
            return Visibility.PACKAGE;
        }
    }

    private static Constructor createConstructor(ConstructorDeclaration declaration) {
        Constructor constructor = new Constructor(declaration.getName());
        constructor.setVisibility(getVisibility(declaration));

        LinkedHashSet<MethodArgument> arguments = constructor.getArguments();

        for (Parameter parameter : safe(declaration.getParameters())) {
            MethodArgument arg = new MethodArgument(parameter.getType().toString(), parameter.getId().getName());
            arguments.add(arg);
        }

        return constructor;
    }

    private static Visibility getVisibility(ConstructorDeclaration declaration) {
        int modifiers = declaration.getModifiers();
        if (ModifierSet.isPublic(modifiers)) {
            return Visibility.PUBLIC;
        } else if (ModifierSet.isProtected(modifiers)) {
            return Visibility.PROTECTED;
        } else if (ModifierSet.isPrivate(modifiers)) {
            return Visibility.PRIVATE;
        } else {
            return Visibility.PACKAGE;
        }
    }

    private static Literal createLiteral(EnumConstantDeclaration declaration) {
        Literal literal = new Literal(declaration.getName());
        return literal;
    }

    private static List<RelationBase> createRelations(File file, Map<String, ComponentBase> componentsByName) throws IOException {
        List<RelationBase> relations = new LinkedList<>();
        try {
            CompilationUnit cu = JavaParser.parse(file, "UTF-8");
            String sourcePackage = packageName(cu);
            TypeResolver types = new TypeResolver(sourcePackage, safe(cu.getImports()), componentsByName);
            for (TypeDeclaration type : safe(cu.getTypes())) {
                if (!(type instanceof ClassOrInterfaceDeclaration)) {
                    continue;
                }
                ClassOrInterfaceDeclaration declaration = (ClassOrInterfaceDeclaration) type;
                ComponentBase source = componentsByName.get(qualify(sourcePackage, declaration.getName()));
                if (source == null) {
                    continue;
                }

                for (ClassOrInterfaceType parent : safe(declaration.getExtends())) {
                    addRelation(relations, new IsRelation(), source, types.findType(parent));
                }
                for (ClassOrInterfaceType parent : safe(declaration.getImplements())) {
                    addRelation(relations, new ImplementsRelation(), source, types.findType(parent));
                }
                for (BodyDeclaration member : safe(declaration.getMembers())) {
                    if (member instanceof FieldDeclaration && !declaration.isInterface()) {
                        addFieldRelations(relations, source, (FieldDeclaration) member, types);
                    } else if (member instanceof MethodDeclaration) {
                        addMethodRelation(relations, source, (MethodDeclaration) member, types);
                    }
                }
            }
        } catch (ParseException | com.github.javaparser.TokenMgrError | RuntimeException ex) {
            throw new IOException("Cannot reverse engineer " + file + ": invalid or unsupported Java syntax. " + ex.getMessage(), ex);
        }
        return relations;
    }

    private static void addFieldRelations(List<RelationBase> relations, ComponentBase source,
            FieldDeclaration field, TypeResolver types) {
        Type type = field.getType();
        if (type instanceof ReferenceType && ((ReferenceType) type).getArrayCount() > 0) {
            return;
        }
        ComponentBase target = types.findReferencedType(type);
        if (target == null) {
            return;
        }
        String typeName = type.toStringWithoutComments();
        boolean collection = typeName.contains("List") || typeName.contains("Set") || typeName.contains("Map")
                || typeName.contains("Queue") || typeName.contains("Deque");
        for (VariableDeclarator variable : field.getVariables()) {
            if (variable.getId().getArrayCount() > 0) {
                continue;
            }
            AggregationRelation relation = new AggregationRelation();
            relation.setName(variable.getId().getName());
            relation.setCardinalitySource(CardinalityEnum.One2One);
            relation.setCardinalityTarget(collection ? CardinalityEnum.Zero2Many : CardinalityEnum.One2One);
            if (collection) {
                relation.setCollectionType(typeName);
            }
            addRelation(relations, relation, source, target);
        }
    }

    private static void addMethodRelation(List<RelationBase> relations, ComponentBase source,
            MethodDeclaration method, TypeResolver types) {
        ComponentBase target = types.findReferencedType(method.getType());
        for (Parameter parameter : safe(method.getParameters())) {
            if (target != null) {
                break;
            }
            target = types.findReferencedType(parameter.getType());
        }
        if (target != null) {
            UseRelation relation = new UseRelation();
            relation.setCardinalitySource(CardinalityEnum.One2One);
            relation.setCardinalityTarget(CardinalityEnum.One2One);
            addRelation(relations, relation, source, target);
        }
    }

    private static void addRelation(List<RelationBase> relations, RelationBase relation,
            ComponentBase source, ComponentBase target) {
        if (target != null && relation.canConnect(source, target)) {
            relation.setSource(source);
            relation.setTarget(target);
            relations.add(relation);
        }
    }

    private static String packageName(CompilationUnit source) {
        return source.getPackage() == null ? "" : source.getPackage().getName().toStringWithoutComments();
    }

    private static String qualify(String scope, String name) {
        return scope.isEmpty() ? name : scope + "." + name;
    }

    private static String qualifiedTypeName(ClassOrInterfaceType type) {
        return type.getScope() == null ? type.getName() : qualify(qualifiedTypeName(type.getScope()), type.getName());
    }

    /**
     * Resolves references to the top-level types included in the diagram.
     */
    private static final class TypeResolver {

        private final String sourcePackage;
        private final List<ImportDeclaration> imports;
        private final Map<String, ComponentBase> componentsByName;

        private TypeResolver(String sourcePackage, List<ImportDeclaration> imports, Map<String, ComponentBase> componentsByName) {
            this.sourcePackage = sourcePackage;
            this.imports = imports;
            this.componentsByName = componentsByName;
        }

        private ComponentBase findType(ClassOrInterfaceType type) {
            if (type.getScope() != null) {
                return componentsByName.get(qualifiedTypeName(type));
            }
            if (isTypeParameter(type)) {
                return null;
            }
            String name = type.getName();
            String explicitImport = null;
            for (ImportDeclaration imported : imports) {
                if (!imported.isStatic() && !imported.isAsterisk() && name.equals(imported.getName().getName())) {
                    String qualifiedName = imported.getName().toStringWithoutComments();
                    if (explicitImport != null && !explicitImport.equals(qualifiedName)) {
                        return null;
                    }
                    explicitImport = qualifiedName;
                }
            }
            if (explicitImport != null) {
                return componentsByName.get(explicitImport);
            }

            String samePackage = qualify(sourcePackage, name);
            if (componentsByName.containsKey(samePackage)) {
                return componentsByName.get(samePackage);
            }

            String javaLang = qualify("java.lang", name);
            String match = componentsByName.containsKey(javaLang) ? javaLang : null;
            for (ImportDeclaration imported : imports) {
                if (!imported.isStatic() && imported.isAsterisk()) {
                    String candidate = qualify(imported.getName().toStringWithoutComments(), name);
                    if (componentsByName.containsKey(candidate)) {
                        if (match != null && !match.equals(candidate)) {
                            return null;
                        }
                        match = candidate;
                    }
                }
            }
            return match == null ? null : componentsByName.get(match);
        }

        private ComponentBase findReferencedType(Type type) {
            if (type instanceof ReferenceType) {
                return findReferencedType(((ReferenceType) type).getType());
            }
            if (type instanceof WildcardType) {
                WildcardType wildcard = (WildcardType) type;
                return findReferencedType(wildcard.getExtends() != null ? wildcard.getExtends() : wildcard.getSuper());
            }
            if (type instanceof ClassOrInterfaceType) {
                ClassOrInterfaceType declared = (ClassOrInterfaceType) type;
                ComponentBase match = findType(declared);
                for (Type argument : safe(declared.getTypeArgs())) {
                    if (match != null) {
                        break;
                    }
                    match = findReferencedType(argument);
                }
                return match;
            }
            return null;
        }

        private static boolean isTypeParameter(ClassOrInterfaceType type) {
            String name = type.getName();
            for (Node owner = type.getParentNode(); owner != null; owner = owner.getParentNode()) {
                if (owner instanceof ClassOrInterfaceDeclaration
                        && safe(((ClassOrInterfaceDeclaration) owner).getTypeParameters()).stream()
                                .anyMatch(parameter -> name.equals(parameter.getName()))) {
                    return true;
                }
                if (owner instanceof MethodDeclaration
                        && safe(((MethodDeclaration) owner).getTypeParameters()).stream()
                                .anyMatch(parameter -> name.equals(parameter.getName()))) {
                    return true;
                }
            }
            return false;
        }
    }

    static Point nextComponentPosition = new Point(20, 20);

    private static Point getNextComponentPosition() {
        Point oldPoint = new Point();
        if (nextComponentPosition.getX() > 2120) {
            nextComponentPosition.move(20, (int) nextComponentPosition.getY() + 400);
        }
        oldPoint.x = (int) nextComponentPosition.getX();
        oldPoint.y = (int) nextComponentPosition.getY();
        nextComponentPosition.translate(300, 0);
        return oldPoint;
    }

    private static void resetNextComponentPosition() {
        nextComponentPosition = new Point(20, 20);
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> safe(List<T> other) {
        return other == null ? Collections.EMPTY_LIST : other;
    }
}
