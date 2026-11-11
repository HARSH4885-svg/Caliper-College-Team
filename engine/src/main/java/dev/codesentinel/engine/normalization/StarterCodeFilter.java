package dev.codesentinel.engine.normalization;

import dev.codesentinel.engine.fingerprint.KGramHasher;
import dev.codesentinel.engine.fingerprint.TokenHasher;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Starter/common-code suppression as its own pipeline stage (FR-PLAG-08, FR-PLAG-21).
 *
 * <p>A student token is marked <em>suppressed</em> when it lies inside a k-token window that equals a window of the
 * instructor's starter code. Matching is done on a <em>structural key</em> (identifiers collapsed to ID, numbers to NUM,
 * strings to STR) because identifier numbering depends on what a student wrote earlier in the file, so exact ID_n
 * values are not stable for starter regions. Candidate windows are found by hash and then <b>verified by comparing the
 * actual keys</b>, so a hash collision can never hide unrelated student code.
 *
 * <p>Suppressed tokens are excluded from fingerprints and from the structural matcher, and are counted in the report
 * ("evidence accounting"), so the instructor can see how much was discounted.
 */
public final class StarterCodeFilter {

    private final int k;
    private final Map<Long, List<int[]>> windows = new HashMap<>();   // hash -> (fileIdx, start)
    private final List<String[]> starterKeys = new ArrayList<>();

    public StarterCodeFilter(int k) {
        this.k = k;
    }

    public boolean isEmpty() {
        return windows.isEmpty();
    }

    /** Registers one normalized starter file. */
    public void addStarterFile(List<NormalizedToken> tokens) {
        String[] keys = structuralKeys(tokens);
        int fileIdx = starterKeys.size();
        starterKeys.add(keys);
        long[] h = KGramHasher.hashes(TokenHasher.hashAll(keys), k);
        for (int p = 0; p < h.length; p++) {
            windows.computeIfAbsent(h[p], x -> new ArrayList<>()).add(new int[]{fileIdx, p});
        }
    }

    /** Returns a mask with true for every token covered by a verified starter window. */
    public boolean[] suppressionMask(List<NormalizedToken> tokens) {
        boolean[] mask = new boolean[tokens.size()];
        if (windows.isEmpty() || tokens.size() < k) return mask;
        String[] keys = structuralKeys(tokens);
        long[] h = KGramHasher.hashes(TokenHasher.hashAll(keys), k);
        for (int p = 0; p < h.length; p++) {
            List<int[]> cands = windows.get(h[p]);
            if (cands == null) continue;
            for (int[] c : cands) {
                if (verified(keys, p, starterKeys.get(c[0]), c[1])) {
                    for (int j = p; j < p + k; j++) mask[j] = true;
                    break;
                }
            }
        }
        return mask;
    }

    private boolean verified(String[] a, int pa, String[] b, int pb) {
        for (int j = 0; j < k; j++) {
            if (!a[pa + j].equals(b[pb + j])) return false;
        }
        return true;
    }

    static String[] structuralKeys(List<NormalizedToken> tokens) {
        String[] keys = new String[tokens.size()];
        for (int i = 0; i < keys.length; i++) {
            String v = tokens.get(i).value();
            if (v.equals("ID") || v.startsWith("ID_")) keys[i] = "ID";
            else if (v.startsWith("NUM")) keys[i] = v.startsWith("NUMF") ? "NUMF" : "NUM";
            else if (v.startsWith("STR")) keys[i] = "STR";
            else if (v.startsWith("CHR")) keys[i] = "CHR";
            else keys[i] = v;
        }
        return keys;
    }
}
