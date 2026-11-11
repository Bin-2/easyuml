package org.uml.reveng;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import org.netbeans.api.java.project.JavaProjectConstants;
import org.netbeans.api.java.queries.UnitTestForSourceQuery;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectUtils;
import org.netbeans.api.project.SourceGroup;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

/**
 * Collects the Java sources configured by the selected project's provider.
 */
final class ProjectJavaSources {

    private ProjectJavaSources() {
    }

    static Collection<File> mainSources(Project project) throws IOException {
        SourceGroup[] groups = ProjectUtils.getSources(project)
                .getSourceGroups(JavaProjectConstants.SOURCES_TYPE_JAVA);
        Set<FileObject> testRoots = new HashSet<>();
        for (SourceGroup group : groups) {
            FileObject root = group.getRootFolder();
            if (UnitTestForSourceQuery.findSources(root).length > 0) {
                testRoots.add(root);
            }
        }

        Set<File> files = new LinkedHashSet<>();
        for (SourceGroup group : groups) {
            ArrayDeque<FileObject> folders = new ArrayDeque<>();
            Set<File> visited = new HashSet<>();
            folders.add(group.getRootFolder());
            while (!folders.isEmpty()) {
                FileObject folder = folders.removeFirst();
                if (isTestSource(folder, testRoots) || !group.contains(folder)) {
                    continue;
                }
                if (!visited.add(localFile(folder))) {
                    continue;
                }
                for (FileObject child : folder.getChildren()) {
                    if (!group.contains(child) || isTestSource(child, testRoots)) {
                        continue;
                    }
                    if (child.isFolder()) {
                        folders.addLast(child);
                    } else if (child.isData() && "java".equals(child.getExt())) {
                        files.add(localFile(child));
                    }
                }
            }
        }
        if (files.isEmpty()) {
            throw new IOException("No Java source files were found in the configured main source folders of "
                    + ProjectUtils.getInformation(project).getDisplayName()
                    + ". Select a Java source project, package, or source file.");
        }
        return files;
    }

    private static boolean isTestSource(FileObject file, Set<FileObject> testRoots) {
        return testRoots.stream().anyMatch(root -> root.equals(file) || FileUtil.isParentOf(root, file));
    }

    private static File localFile(FileObject file) throws IOException {
        File local = FileUtil.toFile(file);
        if (local == null) {
            throw new IOException("Reverse engineering requires local Java sources: " + file.getPath());
        }
        // Deduplicate overlapping roots and do not revisit a symbolic-link cycle.
        return local.getCanonicalFile();
    }
}
