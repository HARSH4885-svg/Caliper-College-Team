package dev.codesentinel.storage;

import java.io.InputStream;

/** @param key logical relative key chosen by the application, never a client-supplied path. */
public record StoreRequest(String key, InputStream content) {
}
