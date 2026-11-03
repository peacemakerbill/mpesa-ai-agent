package com.darajaagentx.api.storage;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * A {@link FileStore} on a folder. The folder can be on the server's own disk or be a network share mounted there:
 * for the program there is no difference, which is why one class serves both settings.
 *
 * <p>Keys may contain folders but nothing else that could leave the root: only letters, digits, dot, dash and
 * underscore between slashes, and no {@code ..}. Files are written to a temporary name first and then moved, so a
 * reader never sees half a file.
 */
public final class DiskFileStore implements FileStore {

    private static final Pattern KEY = Pattern.compile("^[A-Za-z0-9_-][A-Za-z0-9._-]{0,120}(/[A-Za-z0-9_-][A-Za-z0-9._-]{0,120}){0,3}$");

    private final Path root;
    private final String label;

    public DiskFileStore(Path root, String label) {
        this.root = root.toAbsolutePath().normalize();
        this.label = label;
    }

    /** Opens a local folder, creating it when needed. */
    public static DiskFileStore local(Path folder) {
        try {
            Files.createDirectories(folder);
        } catch (IOException e) {
            throw new StorageException("The local storage folder " + folder + " cannot be created: " + e.getMessage(), e);
        }
        return check(new DiskFileStore(folder, "local folder " + folder.toAbsolutePath().normalize()));
    }

    /**
     * Opens a folder on a mounted network share. The share itself must already be mounted: when it is not, this fails
     * with a clear message instead of quietly filling a folder on the server's own disk.
     */
    public static DiskFileStore network(Path mount, String subfolder, boolean createIfMissing) {
        if (mount == null || mount.toString().isBlank()) {
            throw new StorageException("agentx.storage.network.path is empty. Give the folder where the network share is mounted.");
        }
        if (!Files.isDirectory(mount)) {
            if (!createIfMissing) {
                throw new StorageException("The network storage folder " + mount + " does not exist. Mount the share there first, "
                        + "or set agentx.storage.network.create-if-missing to true.");
            }
            try {
                Files.createDirectories(mount);
            } catch (IOException e) {
                throw new StorageException("The network storage folder " + mount + " cannot be created: " + e.getMessage(), e);
            }
        }
        Path folder = subfolder == null || subfolder.isBlank() ? mount : mount.resolve(subfolder.trim());
        try {
            Files.createDirectories(folder);
        } catch (IOException e) {
            throw new StorageException("The network storage folder " + folder + " cannot be used: " + e.getMessage(), e);
        }
        return check(new DiskFileStore(folder, "network share " + folder.toAbsolutePath().normalize()));
    }

    private static DiskFileStore check(DiskFileStore store) {
        if (!store.healthy()) {
            throw new StorageException("Cannot write to the " + store.describe() + ". Check that the folder is writable by the user that runs this service.");
        }
        return store;
    }

    @Override
    public void put(String key, byte[] data) {
        Path target = resolve(key);
        Path temp = target.resolveSibling(target.getFileName() + "." + UUID.randomUUID().toString().substring(0, 8) + ".tmp");
        try {
            Files.createDirectories(target.getParent());
            Files.write(temp, data, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                // some network file systems cannot move atomically
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            try {
                Files.deleteIfExists(temp);
            } catch (IOException ignored) {
                // nothing more to do
            }
            throw new StorageException("The file could not be saved to the " + label + ".", e);
        }
    }

    @Override
    public Optional<byte[]> get(String key) {
        Path file = resolve(key);
        try {
            return Files.isRegularFile(file) ? Optional.of(Files.readAllBytes(file)) : Optional.empty();
        } catch (IOException e) {
            throw new StorageException("The file could not be read from the " + label + ".", e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            throw new StorageException("The file could not be removed from the " + label + ".", e);
        }
    }

    @Override
    public String describe() {
        return label;
    }

    @Override
    public boolean healthy() {
        Path probe = root.resolve(".health-" + UUID.randomUUID().toString().substring(0, 8));
        try {
            Files.createDirectories(root);
            byte[] sample = {1, 2, 3};
            Files.write(probe, sample);
            return java.util.Arrays.equals(sample, Files.readAllBytes(probe));
        } catch (IOException e) {
            return false;
        } finally {
            try {
                Files.deleteIfExists(probe);
            } catch (IOException ignored) {
                // a leftover probe file is harmless
            }
        }
    }

    private Path resolve(String key) {
        if (key == null || !KEY.matcher(key).matches() || key.contains("..")) {
            throw new StorageException("That file name is not allowed.");
        }
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) {
            throw new StorageException("That file name is not allowed.");
        }
        return path;
    }
}
