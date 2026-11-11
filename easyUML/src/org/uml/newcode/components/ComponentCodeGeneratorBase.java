package org.uml.newcode.components;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseException;
import com.github.javaparser.TokenMgrError;
import com.github.javaparser.ast.CompilationUnit;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.lang.model.SourceVersion;
import org.uml.filetype.cdg.renaming.MyClassDiagramRenameTable;
import org.uml.filetype.cdg.renaming.MyComponentsRenameTable;
import org.uml.model.AtomicFiles;
import org.uml.model.components.ComponentBase;

/**
 * Renders fresh source code and creates missing files without replacing code.
 */
public abstract class ComponentCodeGeneratorBase<T extends ComponentBase> {

    protected abstract String generateCode(T component);

    protected abstract String updateCode(T component, MyClassDiagramRenameTable renames, CompilationUnit cu) throws IOException;

    protected static String originalSignature(ComponentBase component, MyClassDiagramRenameTable renames) {
        MyComponentsRenameTable componentRenames = renames.getComponentRenames();
        return componentRenames.contains(component)
                ? componentRenames.getOriginalSignature(component) : component.getSignature();
    }

    public final byte[] generateNewCode(T component) throws IOException {
        sourceFile(Paths.get("."), component);
        try {
            String code = generateCode(component);
            if (code == null || code.trim().isEmpty()) {
                throw new IOException("Generator produced empty output for " + component.getSignature());
            }
            JavaParser.parse(new StringReader(code), false);
            return code.getBytes(StandardCharsets.UTF_8);
        } catch (ParseException | TokenMgrError | RuntimeException ex) {
            throw new IOException("Cannot generate " + component.getSignature()
                    + ". Invalid or unsupported Java syntax. " + ex.getMessage(), ex);
        }
    }

    /**
     * Compatibility entry point. Existing sources are skipped and rename
     * tracking is preserved.
     */
    @Deprecated
    public void generateOrUpdateCode(T component, MyClassDiagramRenameTable renames,
            String sourcePath) throws IOException {
        Path target = sourceFile(Paths.get(sourcePath), component);
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            AtomicFiles.create(target, generateNewCode(component));
        }
    }

    public static Path sourceFile(Path sourceRoot, ComponentBase component) throws IOException {
        String name = component.getName();
        String packageName = component.getParentPackage();
        if (name == null || !SourceVersion.isIdentifier(name) || packageName == null) {
            throw new IOException("Invalid Java class or package name for " + name);
        }
        if (!packageName.isEmpty()) {
            for (String part : packageName.split("\\.", -1)) {
                if (!SourceVersion.isIdentifier(part)) {
                    throw new IOException("Invalid Java package name " + packageName);
                }
            }
        }
        // The generated source is parsed separately with the Java 8 parser.
        // Filesystem mapping must also allow names that became reserved in later JDKs.
        Path root = sourceRoot.toAbsolutePath().normalize();
        Path target = root.resolve(component.getSignature().replace('.', '/') + ".java").normalize();
        if (!target.startsWith(root)) {
            throw new IOException("Source path escapes the selected folder");
        }
        return target;
    }
}
