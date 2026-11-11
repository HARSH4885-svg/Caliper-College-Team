package dev.codesentinel.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.regex.Pattern;

/** Private local storage. Keys are validated so they can never escape the root directory. */
public class LocalFileStorageProvider implements FileStorageProvider {
    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}(/[A-Za-z0-9][A-Za-z0-9._-]{0,127}){0,7}");
    private final Path root;

    public LocalFileStorageProvider(Path root) {
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new StorageException("Cannot create storage root", e);
        }
        this.root = root.toAbsolutePath().normalize();
    }

    public String name() {
        return "LOCAL";
    }

    private Path resolve(String key) {
        if (key == null || !KEY.matcher(key).matches() || key.contains("..")) throw new StorageException("Invalid storage key");
        Path p = root.resolve(key).normalize();
        if (!p.startsWith(root)) throw new StorageException("Invalid storage key");
        return p;
    }

    public StoredObject store(StoreRequest request) {
        Path target = resolve(request.key());
        try {
            Files.createDirectories(target.getParent());
            Path tmp = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                long size;
                try (InputStream in = new DigestInputStream(request.content(), md); OutputStream out = Files.newOutputStream(tmp)) {
                    size = in.transferTo(out);
                }
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                return new StoredObject(name(), request.key(), size, HexFormat.of().formatHex(md.digest()));
            } finally {
                Files.deleteIfExists(tmp);
            }
        } catch (IOException | java.security.NoSuchAlgorithmException e) {
            throw new StorageException("Store failed", e);
        }
    }

    public InputStream read(String key) {
        try {
            return Files.newInputStream(resolve(key));
        } catch (IOException e) {
            throw new StorageException("Object not found", e);
        }
    }

    public boolean exists(String key) {
        return Files.isRegularFile(resolve(key));
    }

    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            throw new StorageException("Delete failed", e);
        }
    }
}
