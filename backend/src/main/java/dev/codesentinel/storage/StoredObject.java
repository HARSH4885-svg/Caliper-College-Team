package dev.codesentinel.storage;

public record StoredObject(String provider, String key, long sizeBytes, String sha256) {
}
