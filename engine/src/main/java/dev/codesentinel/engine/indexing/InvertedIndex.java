package dev.codesentinel.engine.indexing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Inverted fingerprint index {@code hash -> submissions containing it} (FR-PLAG-13, NFR-PERF-01).
 *
 * <p>Candidate pairs are produced by walking posting lists instead of comparing all N*(N-1)/2 pairs. Pairs are
 * canonical ({@code a < b}), so (A,B) and (B,A) can never both appear and there are no self-pairs. Fingerprints that
 * appear in more than {@code maxPostings} submissions are skipped when generating candidates (they are boilerplate by
 * definition); they still count toward document frequencies and exact Dice scores.
 */
public final class InvertedIndex {

    public record CandidatePair(int a, int b) {
    }

    private final Map<Long, List<Integer>> postings = new HashMap<>();
    private final int submissionCount;

    public InvertedIndex(List<Set<Long>> fingerprintSets) {
        this.submissionCount = fingerprintSets.size();
        for (int i = 0; i < fingerprintSets.size(); i++) {
            for (long h : fingerprintSets.get(i)) {
                postings.computeIfAbsent(h, x -> new ArrayList<>()).add(i);
            }
        }
    }

    public int submissionCount() {
        return submissionCount;
    }

    /** Number of submissions containing this fingerprint (document frequency). */
    public int documentFrequency(long hash) {
        List<Integer> p = postings.get(hash);
        return p == null ? 0 : p.size();
    }

    public Set<CandidatePair> candidatePairs(int maxPostings) {
        Set<CandidatePair> pairs = new HashSet<>();
        for (List<Integer> list : postings.values()) {
            if (list.size() < 2 || list.size() > maxPostings) continue;
            for (int x = 0; x < list.size(); x++) {
                for (int y = x + 1; y < list.size(); y++) {
                    int a = Math.min(list.get(x), list.get(y));
                    int b = Math.max(list.get(x), list.get(y));
                    pairs.add(new CandidatePair(a, b));
                }
            }
        }
        return pairs;
    }
}
