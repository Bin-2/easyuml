package org.uml.newcode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.uml.filetype.cdg.renaming.MyClassDiagramRenameTable;
import org.uml.model.AtomicFiles;
import org.uml.model.ClassDiagram;
import org.uml.model.components.ClassComponent;
import org.uml.model.components.ComponentBase;
import org.uml.model.components.EnumComponent;
import org.uml.model.components.InterfaceComponent;
import org.uml.newcode.components.ClassCodeGenerator;
import org.uml.newcode.components.ComponentCodeGeneratorBase;
import org.uml.newcode.components.EnumCodeGenerator;
import org.uml.newcode.components.InterfaceCodeGenerator;

/**
 * Prepares the complete set of missing sources before creating any files.
 *
 * @author Boris Perović
 */
public class ClassDiagramCodeGenerator {

    /**
     * Compatibility entry point. Generation now only creates missing sources.
     */
    @Deprecated
    public static void generateOrUpdateCode(ClassDiagram diagram, MyClassDiagramRenameTable renames,
            String sourcePath) throws IOException {
        Path root = Paths.get(sourcePath);
        prepare(diagram, root, Collections.singletonList(root)).writeMissing();
    }

    public static Plan prepare(ClassDiagram diagram, Path sourceRoot, Collection<Path> otherRoots) throws IOException {
        Path root = sourceRoot.toAbsolutePath().normalize();
        Map<Path, ComponentBase> targets = new LinkedHashMap<>();
        for (ComponentBase component : diagram.getComponents()) {
            Path target = ComponentCodeGeneratorBase.sourceFile(root, component);
            if (targets.put(target, component) != null) {
                throw new IOException("Several diagram types map to the same Java file " + target);
            }
        }
        Plan plan = new Plan(root, otherRoots);
        for (Map.Entry<Path, ComponentBase> entry : targets.entrySet()) {
            Path target = entry.getKey();
            Path existing = plan.existingFile(target);
            if (existing != null) {
                plan.skipped.add(existing);
                continue;
            }
            ComponentBase component = entry.getValue();
            byte[] code;
            if (component instanceof ClassComponent) {
                code = ClassCodeGenerator.getInstance().generateNewCode((ClassComponent) component);
            } else if (component instanceof InterfaceComponent) {
                code = InterfaceCodeGenerator.getInstance().generateNewCode((InterfaceComponent) component);
            } else if (component instanceof EnumComponent) {
                code = EnumCodeGenerator.getInstance().generateNewCode((EnumComponent) component);
            } else {
                throw new IOException("Unsupported diagram component " + component.getSignature());
            }
            plan.files.put(target, code);
        }
        return plan;
    }

    public static final class Plan {

        private final Path root;
        private final List<Path> otherRoots;
        private final Map<Path, byte[]> files = new LinkedHashMap<>();
        private final List<Path> skipped = new ArrayList<>();

        private Plan(Path root, Collection<Path> otherRoots) {
            this.root = root;
            this.otherRoots = new ArrayList<>(otherRoots);
        }

        public List<Path> getFiles() {
            return new ArrayList<>(files.keySet());
        }

        public List<Path> getSkipped() {
            return Collections.unmodifiableList(skipped);
        }

        private Path existingFile(Path target) {
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                return target;
            }
            Path relative = root.relativize(target);
            for (Path otherRoot : otherRoots) {
                Path other = otherRoot.resolve(relative);
                if (Files.exists(other, LinkOption.NOFOLLOW_LINKS)) {
                    return other;
                }
            }
            return null;
        }

        public Result writeMissing() throws IOException {
            Result result = new Result(skipped);
            for (Map.Entry<Path, byte[]> entry : files.entrySet()) {
                Path target = entry.getKey();
                Path existing = existingFile(target);
                try {
                    if (existing != null) {
                        result.skipped.add(existing);
                    } else if (AtomicFiles.create(target, entry.getValue())) {
                        result.created.add(target);
                    } else {
                        result.skipped.add(target);
                    }
                } catch (IOException ex) {
                    throw new IOException("Generation stopped after creating " + result.created.size()
                            + " files in " + root + ". " + ex.getMessage(), ex);
                }
            }
            return result;
        }

        void writeToEmptyFolder(Path destination) throws IOException {
            for (Map.Entry<Path, byte[]> entry : files.entrySet()) {
                Path target = destination.resolve(root.relativize(entry.getKey()));
                if (!AtomicFiles.create(target, entry.getValue())) {
                    throw new IOException("A file already exists at " + target);
                }
            }
        }
    }

    public static final class Result {

        private final List<Path> created = new ArrayList<>();
        private final List<Path> skipped;

        private Result(Collection<Path> skipped) {
            this.skipped = new ArrayList<>(skipped);
        }

        public List<Path> getCreated() {
            return Collections.unmodifiableList(created);
        }

        public List<Path> getSkipped() {
            return Collections.unmodifiableList(skipped);
        }
    }
}
