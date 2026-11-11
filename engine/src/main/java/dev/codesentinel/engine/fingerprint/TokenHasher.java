package dev.codesentinel.engine.fingerprint;

import java.nio.charset.StandardCharsets;

/**
 * Maps a normalized token string to a stable 64-bit value using FNV-1a over its UTF-8 bytes. The result depends only
 * on the string, so fingerprints are identical across restarts, JVMs and machines (FR-PLAG-10, FR-PLAG-12).
 */
public final class TokenHasher {
    private static final long FNV_OFFSET = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    private TokenHasher() {
    }

    public static long hash(String token) {
        long h = FNV_OFFSET;
        for (byte b : token.getBytes(StandardCharsets.UTF_8)) {
            h ^= (b & 0xffL);
            h *= FNV_PRIME;
        }
        return h;
    }

    public static long[] hashAll(String[] tokens) {
        long[] out = new long[tokens.length];
        for (int i = 0; i < tokens.length; i++) out[i] = hash(tokens[i]);
        return out;
    }
}
