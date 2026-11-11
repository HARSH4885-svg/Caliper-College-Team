package dev.codesentinel.engine.winnowing;

import dev.codesentinel.engine.fingerprint.Fingerprint;

import java.util.ArrayList;
import java.util.List;

/**
 * Winnowing (Schleimer, Wilkerson, Aiken 2003) with documented deterministic tie-breaking (FR-PLAG-11).
 *
 * <p>For each window of {@code w} consecutive k-gram hashes the minimum hash is selected. If several hashes in the
 * window are equal to the minimum, the <b>rightmost</b> one is chosen. A selection is recorded once even if it is the
 * minimum of several consecutive windows. Guarantee: any shared run of at least {@code w + k - 1} tokens produces at
 * least one shared fingerprint.
 *
 * <p>Boundary rules: no k-grams -> no fingerprints; fewer than {@code w} k-grams -> one window over all of them.
 */
public final class Winnower {
    private Winnower() {
    }

    /**
     * @param hashes    k-gram hashes in stream order
     * @param positions token position of each k-gram (same length as {@code hashes})
     */
    public static List<Fingerprint> winnow(long[] hashes, int[] positions, int w) {
        if (w < 1) throw new IllegalArgumentException("window size must be >= 1");
        List<Fingerprint> out = new ArrayList<>();
        int m = hashes.length;
        if (m == 0) return out;
        int windows = Math.max(1, m - w + 1);
        int width = Math.min(w, m);
        int last = -1;
        for (int start = 0; start < windows; start++) {
            int best = start;
            for (int j = start; j < start + width; j++) {
                if (hashes[j] <= hashes[best]) best = j; // '<=' keeps the rightmost minimum
            }
            if (best != last) {
                out.add(new Fingerprint(hashes[best], positions[best]));
                last = best;
            }
        }
        return out;
    }
}
