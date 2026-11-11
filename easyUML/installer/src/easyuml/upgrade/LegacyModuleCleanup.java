package easyuml.upgrade;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * NBM post-install hook. Runs in the updater's separate JVM after NetBeans has
 * shut down, before its module system and window system load the new package.
 * Uses only the JDK: the running-IDE Auto Update API is unavailable here.
 */
public final class LegacyModuleCleanup {
    private static final String VERSION = "1.7.12";
    private static final List<String> LEGACY_VERSIONS = Arrays.asList("1.4", "1.5", "1.6", "1.7");
    private static final Set<String> LEGACY = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
            "com.github.javaparser", "org.uml.dom4j", "org.uml.explorer",
            "org.uml.filetype", "org.uml.model", "org.uml.newcode",
            "org.uml.project", "org.uml.reveng", "org.uml.visual")));
    private static final String EXPLORER = "easyUMLExplorerTopComponent";
    private final Path profile;
    private final Path platform;
    private final Path report;

    private LegacyModuleCleanup(Path profile, Path platform) throws IOException {
        this.profile = profile;
        this.platform = platform;
        report = local("var/easyuml/nbm-upgrade.log");
    }

    public static void main(String[] arguments) {
        LegacyModuleCleanup cleanup = null;
        try {
            require(arguments.length == 2, "The NetBeans updater must supply the profile and platform directories");
            cleanup = new LegacyModuleCleanup(Paths.get(arguments[0]).toRealPath(),
                    Paths.get(arguments[1]).toRealPath());
            cleanup.run();
        } catch (Exception failure) {
            failure.printStackTrace();
            if (cleanup != null) {
                try {
                    cleanup.log("FAILED: " + failure);
                } catch (IOException reportingFailure) {
                    failure.addSuppressed(reportingFailure);
                    reportingFailure.printStackTrace();
                }
            }
            // The updater logs subprocess failures but does not roll back an NBM.
            // SingleNbmInstall.validate remains the final duplicate-module guard.
            System.exit(1);
        }
    }

    private void run() throws Exception {
        Path code = Paths.get(LegacyModuleCleanup.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI()).toRealPath();
        require(code.equals(local("update/main").toRealPath()),
                "Automatic migration requires a Plugin Manager installation in the current user directory");
        require(!profile.startsWith(platform.getParent()), "Refusing to modify the NetBeans installation");
        Attributes installed = manifest(local("modules/easyuml.jar"));
        require("easyuml".equals(installed.getValue("OpenIDE-Module"))
                && VERSION.equals(installed.getValue("OpenIDE-Module-Specification-Version")),
                "The consolidated easyUML " + VERSION + " module has not been installed");
        log("Starting easyUML " + VERSION + " NBM migration in " + profile);

        Set<String> found = new LinkedHashSet<>();
        Set<Path> removal = new LinkedHashSet<>();
        Set<String> providedTokens = new LinkedHashSet<>();
        for (String name : LEGACY.stream().sorted().collect(Collectors.toList())) {
            Path tracking = local(trackingPath(name));
            Path config = local(configPath(name));
            Path jar = local(jarPath(name));
            if (!Files.exists(tracking) && !Files.exists(config) && !Files.exists(jar)) {
                continue;
            }
            Tracking record = tracking(tracking);
            require(name.equals(record.name()) && LEGACY_VERSIONS.contains(record.version()),
                    "Unsupported legacy module " + record.name() + " version " + record.version()
                    + " in " + tracking + "; supported versions: " + LEGACY_VERSIONS);
            require(record.files().contains(configPath(name)) && record.files().contains(jarPath(name)),
                    "Incomplete file ownership record for " + name);
            if (Files.exists(jar)) {
                Attributes attributes = manifest(jar);
                require(name.equals(attributes.getValue("OpenIDE-Module"))
                        && record.version().equals(attributes.getValue("OpenIDE-Module-Specification-Version")),
                        "Module identity differs from its ownership record: " + jar);
                providedTokens.addAll(tokens(attributes.getValue("OpenIDE-Module-Provides")));
            }
            providedTokens.add("cnb." + name);
            // Disable registration first; retain tracking until all its files are gone.
            removal.add(config);
            for (String relative : record.files()) {
                require(ownedPath(record, relative), "Unexpected legacy file in " + tracking + ": " + relative);
                removal.add(local(relative));
            }
            removal.add(tracking);
            found.add(name);
        }
        List<Path> clusters = clusters();
        for (Path cluster : clusters) {
            if (!cluster.equals(profile)) {
                for (String name : LEGACY) {
                    require(!Files.exists(cluster.resolve(configPath(name)))
                            && !Files.exists(cluster.resolve(jarPath(name)))
                            && !Files.exists(cluster.resolve(trackingPath(name))),
                            "Legacy module " + name + " is outside this profile: " + cluster);
                }
            }
        }
        if (found.isEmpty()) {
            log("SUCCESS: no obsolete easyUML modules are installed.");
            return;
        }
        verifyOtherOwners(removal);
        // A capability retained by the consolidated module (such as SVG
        // loading) remains available to other plugins after the upgrade.
        providedTokens.removeAll(tokens(installed.getValue("OpenIDE-Module-Provides")));
        verifyDependents(clusters, found, providedTokens);

        Map<Path, byte[]> layoutChanges = new LinkedHashMap<>();
        Set<Path> layoutFiles = savedWindows(layoutChanges);
        Path backups = local("var/easyuml/nbm-upgrade-backups");
        Files.createDirectories(backups);
        Path backup = Files.createTempDirectory(backups, "before-" + VERSION + "-");
        Set<Path> originalFiles = new LinkedHashSet<>(removal);
        originalFiles.addAll(layoutFiles);
        // Complete the backup before changing any installed file.
        for (Path file : originalFiles) {
            if (Files.exists(file)) {
                require(Files.isRegularFile(file), "Expected a regular file: " + file);
                Path saved = backup.resolve(profile.relativize(file));
                Files.createDirectories(saved.getParent());
                Files.copy(file, saved, StandardCopyOption.COPY_ATTRIBUTES);
            }
        }
        Files.write(backup.resolve("README.txt"),
                ("Original easyUML module files and saved window layout before the " + VERSION + " NBM upgrade.\n"
                + "Profile: " + profile + "\nModules: " + String.join(", ", found) + "\n"
                + "NetBeans separately backs up the replaced easyuml umbrella under update/backup.\n").getBytes(StandardCharsets.UTF_8));
        log("Backup complete: " + backup);

        List<Path> changed = new ArrayList<>();
        try {
            for (Map.Entry<Path, byte[]> entry : layoutChanges.entrySet()) {
                changed.add(entry.getKey());
                replace(entry.getKey(), entry.getValue());
            }
            for (Path file : removal) {
                if (Files.exists(file)) {
                    changed.add(file);
                    Files.delete(file);
                }
            }
            log("SUCCESS: removed " + found.size() + " obsolete modules: " + String.join(", ", found)
                    + ". Saved window layout retained; " + layoutChanges.size() + " module references updated.");
        } catch (Exception failure) {
            for (int i = changed.size() - 1; i >= 0; i--) {
                Path file = changed.get(i);
                try {
                    replace(file, Files.readAllBytes(backup.resolve(profile.relativize(file))));
                } catch (IOException restoreFailure) {
                    failure.addSuppressed(restoreFailure);
                }
            }
            throw new IOException("Migration could not finish; restored changed files where possible. Backup: "
                    + backup, failure);
        }
        // The updater touches the cluster's .lastModified after this hook returns,
        // writes easyuml's NEW tracking record and invalidates the startup caches.
    }

    private void verifyOtherOwners(Set<Path> removal) throws Exception {
        for (Path file : xmlFiles(local("update_tracking"))) {
            if (removal.contains(file) || file.getFileName().toString().equals("easyuml.xml")) {
                // easyuml.xml still describes the PREVIOUS version at this point.
                // NetBeans writes the replacement record after the hook returns.
                continue;
            }
            for (String relative : tracking(file).files()) {
                require(!removal.contains(local(relative)), "A remaining plugin also owns " + relative + ": " + file);
            }
        }
    }

    private void verifyDependents(List<Path> clusters, Set<String> removed, Set<String> provided) throws Exception {
        for (Path cluster : clusters) {
            for (Path config : xmlFiles(cluster.resolve("config/Modules"))) {
                Element root = xml(config).getDocumentElement();
                String name = root.getAttribute("name");
                if (removed.contains(name)) {
                    continue;
                }
                String relative = null;
                NodeList parameters = root.getElementsByTagName("param");
                for (int i = 0; i < parameters.getLength(); i++) {
                    Element parameter = (Element) parameters.item(i);
                    if ("jar".equals(parameter.getAttribute("name"))) {
                        relative = parameter.getTextContent().trim();
                    }
                }
                require(relative != null, "No module JAR in " + config);
                Path jar = cluster.resolve(relative).normalize();
                // A userdir config can override a module in an IDE cluster.
                if (!Files.isRegularFile(jar)) {
                    for (Path candidate : clusters) {
                        if (Files.isRegularFile(candidate.resolve(relative))) {
                            jar = candidate.resolve(relative);
                            break;
                        }
                    }
                }
                require(Files.isRegularFile(jar), "Cannot inspect module dependencies: " + jar);
                Attributes attributes = manifest(jar);
                for (String dependency : tokens(attributes.getValue("OpenIDE-Module-Module-Dependencies"))) {
                    String required = dependency.split("[/\\s><=]", 2)[0];
                    require(!removed.contains(required), "Plugin " + name + " still requires " + required);
                }
                for (String key : Arrays.asList("OpenIDE-Module-Requires", "OpenIDE-Module-Needs")) {
                    for (String token : tokens(attributes.getValue(key))) {
                        require(!provided.contains(token), "Plugin " + name + " still requires legacy token " + token);
                    }
                }
            }
        }
    }

    private List<Path> clusters() throws IOException {
        Set<Path> result = new LinkedHashSet<>();
        result.add(profile);
        result.add(platform);
        try (Stream<Path> directories = Files.list(platform.getParent())) {
            for (Path directory : directories.filter(Files::isDirectory).collect(Collectors.toList())) {
                if (Files.isDirectory(directory.resolve("config/Modules"))) {
                    result.add(directory.toRealPath());
                }
            }
        }
        // Extra clusters are read only, including those set for this profile.
        String extras = "";
        for (Path config : Arrays.asList(platform.getParent().resolve("etc/netbeans.conf"), profile.resolve("etc/netbeans.conf"))) {
            if (Files.isRegularFile(config)) {
                for (String line : Files.readAllLines(config)) {
                    String value = line.trim();
                    if (value.startsWith("netbeans_extraclusters=")) {
                        extras = value.substring(value.indexOf('=') + 1).trim().replaceAll("^\"|\"$", "");
                    }
                }
            }
        }
        for (String extra : extras.split(java.io.File.pathSeparator)) {
            if (!isBlank(extra)) {
                Path path = Paths.get(extra);
                require(path.isAbsolute() && Files.isDirectory(path), "Cannot inspect extra cluster " + extra);
                result.add(path.toRealPath());
            }
        }
        return Collections.unmodifiableList(new ArrayList<>(result));
    }

    private Set<Path> savedWindows(Map<Path, byte[]> changes) throws Exception {
        Set<Path> files = new LinkedHashSet<>();
        // Retaining the local files while replacing both modules in the SAME
        // restart prevents the window system dropping the old Explorer's dock.
        try (Stream<Path> entries = Files.walk(local("config"))) {
            for (Path entry : entries.filter(Files::isRegularFile).collect(Collectors.toList())) {
                Path relative = profile.relativize(entry);
                boolean window = false;
                for (Path part : relative) {
                    window |= part.toString().equals("Windows2Local") || part.toString().startsWith("Windows2Local-");
                }
                if (!window) {
                    continue;
                }
                Path file = local(relative.toString());
                files.add(file);
                if (!Arrays.asList(EXPLORER + ".settings", EXPLORER + ".wstcref", EXPLORER + ".wstcgrp")
                        .contains(file.getFileName().toString())) {
                    continue;
                }
                Document document = xml(file);
                boolean modified = false;
                NodeList modules = document.getElementsByTagName("module");
                for (int i = 0; i < modules.getLength(); i++) {
                    Element module = (Element) modules.item(i);
                    if ("org.uml.explorer".equals(module.getAttribute("name"))) {
                        module.setAttribute("name", "easyuml");
                        module.setAttribute("spec", VERSION);
                        module.removeAttribute("impl");
                        modified = true;
                    }
                }
                if (modified) {
                    TransformerFactory factory = TransformerFactory.newInstance();
                    factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
                    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
                    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
                    Transformer transformer = factory.newTransformer();
                    if (document.getDoctype() != null) {
                        if (document.getDoctype().getPublicId() != null) {
                            transformer.setOutputProperty(OutputKeys.DOCTYPE_PUBLIC, document.getDoctype().getPublicId());
                        }
                        if (document.getDoctype().getSystemId() != null) {
                            transformer.setOutputProperty(OutputKeys.DOCTYPE_SYSTEM, document.getDoctype().getSystemId());
                        }
                    }
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    transformer.transform(new DOMSource(document), new StreamResult(output));
                    changes.put(file, output.toByteArray());
                }
            }
        }
        return files;
    }

    private Path local(String relative) throws IOException {
        Path supplied = Paths.get(relative);
        Path path = profile.resolve(supplied).normalize();
        require(!supplied.isAbsolute() && !path.equals(profile) && path.startsWith(profile),
                "Path escapes the current profile: " + relative);
        for (Path part = path; !part.equals(profile); part = part.getParent()) {
            if (Files.exists(part, LinkOption.NOFOLLOW_LINKS)) {
                require(!Files.isSymbolicLink(part) && part.toRealPath().equals(part),
                        "Refusing a redirected profile path: " + part);
            }
        }
        return path;
    }

    private static boolean ownedPath(Tracking record, String path) {
        String name = record.name();
        if (path.equals(configPath(name)) || path.equals(jarPath(name))) {
            return true;
        }
        // Original 1.4 library paths, verified against its installed tracking
        // records. dom4j is shared by two removed modules; the removal set
        // deduplicates it and verifyOtherOwners still protects remaining owners.
        if ("1.4".equals(record.version())) {
            switch (name) {
                case "com.github.javaparser": return path.equals("modules/ext/javaparser-1.0.12-SNAPSHOT.jar");
                case "org.uml.dom4j":
                case "org.uml.filetype": return path.equals("modules/ext/dom4j-1.6.1.jar");
                case "org.uml.newcode": return Arrays.asList("modules/ext/jalopy.jar", "modules/ext/log4j.jar").contains(path);
                case "org.uml.visual": return Arrays.asList(
                        "modules/ext/collections-generic-4.01.jar", "modules/ext/jung-algorithms-2.0.1.jar",
                        "modules/ext/jung-api-2.0.1.jar", "modules/ext/jung-graph-impl-2.0.1.jar",
                        "modules/ext/jung-visualization-2.0.1.jar", "modules/ext/vl-jung-1.0.jar",
                        "modules/ext/vl-jung-extensions-1.0.jar").contains(path);
                default: return false;
            }
        }
        switch (name) {
            case "org.uml.dom4j": return path.equals("modules/ext/dom4j-2.1.5.jar");
            case "org.uml.explorer": return path.startsWith("modules/ext/easyuml-svg/")
                    || path.startsWith("docs/easyuml-svg-licenses/");
            case "org.uml.visual": return path.startsWith("modules/ext/easyuml-export/")
                    || path.startsWith("docs/easyuml-export-licenses/");
            default: return false;
        }
    }

    private static Tracking tracking(Path file) throws Exception {
        Element root = xml(file).getDocumentElement();
        require("module".equals(root.getTagName()), "Invalid ownership record: " + file);
        Element active = null;
        NodeList versions = root.getElementsByTagName("module_version");
        for (int i = 0; i < versions.getLength(); i++) {
            Element version = (Element) versions.item(i);
            if ("true".equals(version.getAttribute("last"))) {
                require(active == null, "Multiple current versions in " + file);
                active = version;
            }
        }
        require(active != null, "Missing current ownership record: " + file);
        Set<String> paths = new LinkedHashSet<>();
        NodeList entries = active.getElementsByTagName("file");
        for (int i = 0; i < entries.getLength(); i++) {
            String path = ((Element) entries.item(i)).getAttribute("name");
            require(!isBlank(path) && !path.contains("\\") && !path.contains(":")
                    && !Paths.get(path).isAbsolute() && Paths.get(path).normalize().toString()
                            .replace('\\', '/').equals(path), "Invalid owned path: " + path);
            paths.add(path);
        }
        return new Tracking(root.getAttribute("codename"), active.getAttribute("specification_version"), paths);
    }

    private static Document xml(Path file) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory.newDocumentBuilder().parse(file.toFile());
    }

    private static Attributes manifest(Path jar) throws IOException {
        try (JarFile archive = new JarFile(jar.toFile())) {
            require(archive.getManifest() != null, "Missing module manifest: " + jar);
            return archive.getManifest().getMainAttributes();
        }
    }

    private static List<Path> xmlFiles(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return Collections.emptyList();
        }
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".xml")).sorted().collect(Collectors.toList());
        }
    }

    private static List<String> tokens(String value) {
        return value == null || isBlank(value) ? Collections.emptyList()
                : Arrays.stream(value.split(",")).map(String::trim).collect(Collectors.toList());
    }

    private static void replace(Path file, byte[] content) throws IOException {
        Path temporary = Files.createTempFile(file.getParent(), "easyuml-upgrade-", ".tmp");
        try {
            Files.write(temporary, content);
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private void log(String message) throws IOException {
        Files.createDirectories(report.getParent());
        Files.write(report, (Instant.now() + " " + message + System.lineSeparator()).getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        System.out.println(message);
    }

    private static String trackingPath(String name) { return "update_tracking/" + name.replace('.', '-') + ".xml"; }
    private static String configPath(String name) { return "config/Modules/" + name.replace('.', '-') + ".xml"; }
    private static String jarPath(String name) { return "modules/" + name.replace('.', '-') + ".jar"; }

    private static void require(boolean condition, String message) throws IOException {
        if (!condition) {
            throw new IOException(message);
        }
    }

    private static boolean isBlank(String value) {
        return value.codePoints().allMatch(Character::isWhitespace);
    }

    private static final class Tracking {
        private final String name;
        private final String version;
        private final Set<String> files;

        Tracking(String name, String version, Set<String> files) {
            this.name = name;
            this.version = version;
            this.files = files;
        }

        String name() { return name; }
        String version() { return version; }
        Set<String> files() { return files; }
    }
}
