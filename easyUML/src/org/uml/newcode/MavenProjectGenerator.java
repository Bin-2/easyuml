package org.uml.newcode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import javax.lang.model.SourceVersion;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.Namespace;
import org.dom4j.QName;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;
import org.uml.model.AtomicFiles;
import org.uml.model.ClassDiagram;

/**
 * Prepares a standalone Maven project and publishes it in a new directory.
 */
public final class MavenProjectGenerator {

    private final Path directory;
    private final ClassDiagramCodeGenerator.Plan sources;
    private final byte[] pom;

    public MavenProjectGenerator(ClassDiagram diagram, Path parent, String name,
            String groupId, String artifactId, int javaVersion) throws IOException {
        if (name.isEmpty() || name.equals(".") || name.equals("..")
                || name.matches(".*[\\\\/:*?\"<>|].*") || name.endsWith(".") || !name.equals(name.trim())) {
            throw new IOException("Enter a valid project folder name.");
        }
        if (!SourceVersion.isName(groupId)) {
            throw new IOException("Enter a group ID such as com.mycompany.");
        }
        if (!artifactId.matches("[A-Za-z0-9_][A-Za-z0-9_.-]*")) {
            throw new IOException("The artifact ID must contain letters, digits, dots, underscores or hyphens.");
        }
        if (javaVersion < 8) {
            throw new IOException("Choose Java 8 or later.");
        }
        directory = parent.toAbsolutePath().normalize().resolve(name);
        if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("The project folder already exists. Choose a new folder name.\n" + directory);
        }
        Path sourceRoot = directory.resolve("src/main/java");
        sources = ClassDiagramCodeGenerator.prepare(diagram, sourceRoot, Collections.emptyList());
        pom = createPom(name, groupId, artifactId, javaVersion);
    }

    public Path getDirectory() {
        return directory;
    }

    public int getSourceCount() {
        return sources.getFiles().size();
    }

    public Path createProject() throws IOException {
        if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("The project folder already exists. Choose a new folder name.\n" + directory);
        }
        Files.createDirectories(directory.getParent());
        Path staging = Files.createTempDirectory(directory.getParent(), ".easyuml-project-");
        try {
            Path sourceRoot = staging.resolve("src/main/java");
            Files.createDirectories(sourceRoot);
            sources.writeToEmptyFolder(sourceRoot);
            if (!AtomicFiles.create(staging.resolve("pom.xml"), pom)) {
                throw new IOException("The temporary project already contains a POM.");
            }
            // A normal move refuses an existing destination. ATOMIC_MOVE may replace it.
            Files.move(staging, directory);
            return directory;
        } catch (IOException | RuntimeException ex) {
            try {
                removeStaging(staging);
            } catch (IOException cleanup) {
                ex.addSuppressed(cleanup);
            }
            throw ex;
        }
    }

    private static byte[] createPom(String name, String groupId, String artifactId, int javaVersion) throws IOException {
        Document document = DocumentHelper.createDocument();
        Element root = document.addElement(QName.get("project", Namespace.get("http://maven.apache.org/POM/4.0.0")));
        root.addNamespace("xsi", "http://www.w3.org/2001/XMLSchema-instance");
        root.addAttribute(QName.get("schemaLocation", Namespace.get("xsi", "http://www.w3.org/2001/XMLSchema-instance")),
                "http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd");
        value(root, "modelVersion", "4.0.0");
        value(root, "groupId", groupId);
        value(root, "artifactId", artifactId);
        value(root, "version", "1.0-SNAPSHOT");
        value(root, "packaging", "jar");
        value(root, "name", name);
        Element properties = child(root, "properties");
        value(properties, "project.build.sourceEncoding", "UTF-8");
        value(properties, "maven.compiler.source", Integer.toString(javaVersion));
        value(properties, "maven.compiler.target", Integer.toString(javaVersion));

        Element plugin = child(child(child(root, "build"), "plugins"), "plugin");
        value(plugin, "groupId", "org.apache.maven.plugins");
        value(plugin, "artifactId", "maven-compiler-plugin");
        // This compiler version also supports the Maven shipped with NetBeans 8.2.
        value(plugin, "version", "3.8.1");

        // JDK 8 uses source/target. Later JDKs can also check the target Java API.
        Element profile = child(child(root, "profiles"), "profile");
        value(profile, "id", "java-release");
        value(child(profile, "activation"), "jdk", "[9,)");
        value(child(profile, "properties"), "maven.compiler.release", Integer.toString(javaVersion));

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        OutputFormat format = OutputFormat.createPrettyPrint();
        format.setEncoding("UTF-8");
        XMLWriter writer = new XMLWriter(buffer, format);
        writer.write(document);
        writer.close();
        return buffer.toByteArray();
    }

    private static Element child(Element parent, String name) {
        return parent.addElement(QName.get(name, parent.getNamespace()));
    }

    private static void value(Element parent, String name, String text) {
        child(parent, name).setText(text);
    }

    private static void removeStaging(Path staging) throws IOException {
        if (!Files.exists(staging, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        Files.walkFileTree(staging, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException error) throws IOException {
                if (error != null) {
                    throw error;
                }
                Files.delete(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
