package dev.codesentinel.engine;

import dev.codesentinel.engine.fingerprint.Fingerprint;
import dev.codesentinel.engine.fingerprint.KGramHasher;
import dev.codesentinel.engine.fingerprint.TokenHasher;
import dev.codesentinel.engine.indexing.InvertedIndex;
import dev.codesentinel.engine.lexer.Language;
import dev.codesentinel.engine.lexer.LexerFactory;
import dev.codesentinel.engine.normalization.NormalizationConfig;
import dev.codesentinel.engine.normalization.NormalizedToken;
import dev.codesentinel.engine.normalization.StarterCodeFilter;
import dev.codesentinel.engine.normalization.TokenNormalizer;
import dev.codesentinel.engine.similarity.Dice;
import dev.codesentinel.engine.similarity.GreedyStringTiling;
import dev.codesentinel.engine.winnowing.Winnower;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AlgorithmsTest {

    // ---- Winnowing ----------------------------------------------------------------------------------------------

    private static List<Fingerprint> win(long[] h, int w) {
        int[] pos = new int[h.length];
        for (int i = 0; i < pos.length; i++) pos[i] = i;
        return Winnower.winnow(h, pos, w);
    }

    @Test
    void winnowingEmptyInputGivesNothing() {
        assertTrue(win(new long[0], 5).isEmpty());
    }

    @Test
    void fewerKgramsThanWindowUsesOneWindowOverAll() {
        List<Fingerprint> f = win(new long[]{9, 4, 7}, 5);
        assertEquals(1, f.size());
        assertEquals(4, f.get(0).hash());
        assertEquals(1, f.get(0).position());
    }

    @Test
    void ties_pick_the_rightmost_minimum_and_record_it_once() {
        List<Fingerprint> f = win(new long[]{5, 3, 3, 7, 9}, 3);
        assertEquals(1, f.size());
        assertEquals(2, f.get(0).position()); // rightmost of the two 3s; later windows keep the same selection
    }

    @Test
    void exactlyOneWindow() {
        List<Fingerprint> f = win(new long[]{8, 2, 6, 4, 9}, 5);
        assertEquals(List.of(new Fingerprint(2, 1)), f);
    }

    @Test
    void windowSizeOneSelectsEveryHash() {
        assertEquals(4, win(new long[]{4, 3, 2, 1}, 1).size());
    }

    @Test
    void winnowingGuaranteeSharedRunOfWPlusKMinusOneTokensSharesAFingerprint() {
        int k = 10, w = 5;
        Random r = new Random(7);
        for (int trial = 0; trial < 300; trial++) {
            String[] shared = new String[w + k - 1];
            for (int i = 0; i < shared.length; i++) shared[i] = "s" + r.nextInt(1000);
            String[] a = embed(shared, r), b = embed(shared, r);
            Set<Long> fa = fingerprints(a, k, w), fb = fingerprints(b, k, w);
            fa.retainAll(fb);
            assertFalse(fa.isEmpty(), "trial " + trial);
        }
    }

    private static String[] embed(String[] shared, Random r) {
        int pre = r.nextInt(40), post = r.nextInt(40);
        String[] out = new String[pre + shared.length + post];
        for (int i = 0; i < pre; i++) out[i] = "p" + r.nextInt(1_000_000);
        System.arraycopy(shared, 0, out, pre, shared.length);
        for (int i = 0; i < post; i++) out[pre + shared.length + i] = "q" + r.nextInt(1_000_000);
        return out;
    }

    private static Set<Long> fingerprints(String[] toks, int k, int w) {
        long[] h = KGramHasher.hashes(TokenHasher.hashAll(toks), k);
        Set<Long> s = new HashSet<>();
        for (Fingerprint f : win(h, w)) s.add(f.hash());
        return s;
    }

    // ---- Dice / index ---------------------------------------------------------------------------------------------

    @Test
    void diceKnownValues() {
        assertEquals(0.5, Dice.coefficient(2, 4, 4), 1e-12);
        assertEquals(1.0, Dice.coefficient(5, 5, 5), 1e-12);
        assertEquals(0.0, Dice.coefficient(0, 3, 9), 1e-12);
        assertEquals(0.0, Dice.coefficient(0, 0, 0), 1e-12); // empty vs empty is not "identical"
    }

    @Test
    void indexProducesCanonicalPairsWithoutSelfOrDuplicates() {
        List<Set<Long>> sets = List.of(Set.of(1L, 2L, 3L), Set.of(2L, 3L), Set.of(3L, 9L), Set.of(77L));
        InvertedIndex idx = new InvertedIndex(sets);
        Set<InvertedIndex.CandidatePair> pairs = idx.candidatePairs(10);
        assertEquals(Set.of(new InvertedIndex.CandidatePair(0, 1), new InvertedIndex.CandidatePair(0, 2),
                new InvertedIndex.CandidatePair(1, 2)), pairs);
        assertTrue(pairs.stream().allMatch(p -> p.a() < p.b()));
        assertEquals(3, idx.documentFrequency(3L));
        assertEquals(0, idx.documentFrequency(12345L));
    }

    @Test
    void veryCommonFingerprintsDoNotGenerateCandidates() {
        List<Set<Long>> sets = new ArrayList<>();
        for (int i = 0; i < 6; i++) sets.add(Set.of(100L)); // shared by everyone = boilerplate
        assertTrue(new InvertedIndex(sets).candidatePairs(3).isEmpty());
    }

    // ---- Greedy String Tiling --------------------------------------------------------------------------------------

    private static int[] seq(int... v) {
        return v;
    }

    private static int[] range(int from, int to) {
        int[] r = new int[to - from];
        for (int i = 0; i < r.length; i++) r[i] = from + i;
        return r;
    }

    private static int[] cat(int[]... parts) {
        int n = 0;
        for (int[] p : parts) n += p.length;
        int[] r = new int[n];
        int o = 0;
        for (int[] p : parts) {
            System.arraycopy(p, 0, r, o, p.length);
            o += p.length;
        }
        return r;
    }

    @Test
    void tilingFindsMovedBlocksAnLcsWouldPenalise() {
        int[] a = cat(range(0, 20), range(100, 121));
        int[] b = cat(range(100, 121), range(0, 20));
        List<GreedyStringTiling.Tile> tiles = GreedyStringTiling.tile(a, b, null, null, 5);
        assertEquals(2, tiles.stream().mapToInt(t -> 1).sum());
        assertEquals(41, tiles.stream().mapToInt(GreedyStringTiling.Tile::length).sum());
    }

    @Test
    void tilingIgnoresMatchesShorterThanMinimum() {
        assertTrue(GreedyStringTiling.tile(seq(1, 2, 3, 4), seq(9, 1, 2, 3, 8), null, null, 4).isEmpty());
        assertEquals(1, GreedyStringTiling.tile(seq(1, 2, 3, 4), seq(9, 1, 2, 3, 4, 8), null, null, 4).size());
    }

    @Test
    void tilesNeverOverlapAndNeverUsePreMarkedPositions() {
        int[] a = range(0, 60);
        int[] b = range(0, 60);
        boolean[] pre = new boolean[60];
        for (int i = 20; i < 30; i++) pre[i] = true;
        List<GreedyStringTiling.Tile> tiles = GreedyStringTiling.tile(a, b, pre, pre, 5);
        boolean[] used = new boolean[60];
        for (var t : tiles) {
            for (int x = t.startA(); x < t.startA() + t.length(); x++) {
                assertFalse(pre[x], "pre-marked position used");
                assertFalse(used[x], "overlapping tiles");
                used[x] = true;
            }
        }
        assertEquals(50, tiles.stream().mapToInt(GreedyStringTiling.Tile::length).sum());
    }

    @Test
    void tilingHandlesEmptyAndShortInputs() {
        assertTrue(GreedyStringTiling.tile(new int[0], range(0, 10), null, null, 3).isEmpty());
        assertTrue(GreedyStringTiling.tile(range(0, 2), range(0, 2), null, null, 3).isEmpty());
    }

    @Test
    void tilingIsLinearishOnLargeIdenticalInputs() {
        int[] a = range(0, 20000);
        long t0 = System.nanoTime();
        var tiles = GreedyStringTiling.tile(a, a.clone(), null, null, 10);
        assertEquals(1, tiles.size());
        assertEquals(20000, tiles.get(0).length());
        assertTrue((System.nanoTime() - t0) / 1_000_000 < 5000, "should not be quadratic in practice");
    }

    // ---- Starter code suppression -----------------------------------------------------------------------------------

    private static List<NormalizedToken> nt(String src) {
        var toks = LexerFactory.forLanguage(Language.C).tokenize(src, "t");
        return new TokenNormalizer().normalize(Language.C, toks, NormalizationConfig.defaults()).tokens();
    }

    @Test
    void starterWindowsAreMaskedEvenWhenIdentifiersAreRenamedOtherCodeIsNot() {
        String starter = "int sumAll(int *p, int n) { int s = 0; for (int i = 0; i < n; i++) { s += p[i]; } return s; }";
        StarterCodeFilter f = new StarterCodeFilter(10);
        f.addStarterFile(nt(starter));
        String student = "int helper(int q) { return q * q - 7; }\n"
                + "int total(int *a, int m) { int acc = 0; for (int j = 0; j < m; j++) { acc += a[j]; } return acc; }\n"
                + "int other(int z) { if (z > 3) return z; return -z; }";
        List<NormalizedToken> toks = nt(student);
        boolean[] mask = f.suppressionMask(toks);
        int masked = 0;
        for (boolean b : mask) if (b) masked++;
        assertTrue(masked >= 20, "starter-shaped function should be masked, got " + masked);
        // the first function (unrelated) is not touched
        for (int i = 0; i < 8; i++) assertFalse(mask[i], "token " + i + " " + toks.get(i).value());
        // the last function is not touched
        for (int i = toks.size() - 8; i < toks.size(); i++) assertFalse(mask[i]);
    }

    @Test
    void emptyStarterMasksNothing() {
        StarterCodeFilter f = new StarterCodeFilter(10);
        assertTrue(f.isEmpty());
        boolean[] mask = f.suppressionMask(nt("int a = 1; int b = 2; int c = 3;"));
        for (boolean b : mask) assertFalse(b);
    }
}
