package org.uml.model;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.StandardOpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Commits a complete file without opening the destination for truncation.
 */
public final class AtomicFiles {

    private AtomicFiles() {
    }

    /**
     * Creates a missing file with an exclusive open, including when another
     * generator creates the same destination after the existence check.
     */
    public static boolean create(Path destination, byte[] bytes) throws IOException {
        Path target = destination.toAbsolutePath().normalize();
        Files.createDirectories(target.getParent());
        OutputStream output;
        try {
            output = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (FileAlreadyExistsException ex) {
            return false;
        }
        try (OutputStream created = output) {
            created.write(bytes);
        } catch (IOException ex) {
            // Only this call's newly created file can reach this cleanup.
            try {
                Files.deleteIfExists(target);
            } catch (IOException cleanup) {
                ex.addSuppressed(cleanup);
            }
            throw new IOException("Could not finish creating " + target, ex);
        }
        return true;
    }

    public static void write(Path destination, byte[] bytes) throws IOException {
        Path target = destination.toAbsolutePath().normalize();
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), ".easyuml-", ".tmp");
        try {
            Files.write(temporary, bytes);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                // Fail safely: an unsupported filesystem must not risk the original.
                throw new IOException("Atomic replacement is unavailable for " + target, ex);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
