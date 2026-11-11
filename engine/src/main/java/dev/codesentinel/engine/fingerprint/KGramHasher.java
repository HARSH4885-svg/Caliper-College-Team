package dev.codesentinel.engine.fingerprint;

/**
 * Deterministic Rabin-Karp rolling hash over k-grams of token hashes (FR-PLAG-09/10).
 *
 * <p>Polynomial hash {@code H = t0*B^(k-1) + t1*B^(k-2) + ... + t(k-1)} evaluated modulo 2^64 using Java's wrapping
 * {@code long} arithmetic (the "unsigned overflow" approach), with an odd 64-bit base B. Rolling update:
 * {@code H' = (H - t_out * B^(k-1)) * B + t_in}. A SplitMix64 finalizer is applied to each result so the
 * minimum-hash selection in Winnowing is uniformly distributed (a raw polynomial hash is biased in its low bits).
 *
 * <p>Collision handling: two different k-grams can share a 64-bit value with probability ~2^-64 per pair; this is
 * accepted for fingerprint scoring (Dice), but every piece of reported <em>evidence</em> is re-verified token-by-token
 * by the structural matcher, so a collision can never create a reported match on its own.
 */
public final class KGramHasher {
    /** Odd constant (golden-ratio derived). */
    static final long BASE = 0x9E3779B97F4A7C15L;

    private KGramHasher() {
    }

    /** Returns {@code n-k+1} k-gram hashes (empty if {@code n < k} or {@code k < 1}). */
    public static long[] hashes(long[] tokenHashes, int k) {
        int n = tokenHashes.length;
        if (k < 1 || n < k) return new long[0];
        long pow = 1;
        for (int i = 0; i < k - 1; i++) pow *= BASE;
        long[] out = new long[n - k + 1];
        long h = 0;
        for (int i = 0; i < k; i++) h = h * BASE + tokenHashes[i];
        out[0] = mix(h);
        for (int i = 1; i < out.length; i++) {
            h = (h - tokenHashes[i - 1] * pow) * BASE + tokenHashes[i + k - 1];
            out[i] = mix(h);
        }
        return out;
    }

    /** Non-rolling reference implementation, used by tests to prove the rolling update is exact. */
    public static long naive(long[] tokenHashes, int from, int k) {
        long h = 0;
        for (int i = 0; i < k; i++) h = h * BASE + tokenHashes[from + i];
        return mix(h);
    }

    static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
