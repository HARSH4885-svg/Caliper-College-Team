package dev.codesentinel.storage;

import java.io.InputStream;

/** Storage port. The application depends on this interface for storage operations; LocalFileStorageProvider is the verified implementation. */
public interface FileStorageProvider {
    String name();

    StoredObject store(StoreRequest request);

    InputStream read(String key);

    boolean exists(String key);

    void delete(String key);
}
