package dev.codesentinel.engine.similarity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Running Karp-Rabin Greedy String Tiling (Wise, 1993) - the structural verification stage (FR-PLAG-17/18).
 *
 * <p>Finds a set of non-overlapping "tiles" (maximal common substrings of at least {@code minMatch} tokens), longest
 * first. Unlike a global LCS it is tolerant to moved blocks (reordered functions), and it uses O(n + m) memory rather
 * than an O(n*m) matrix. Positions pre-marked by the caller (starter code) can never be part of a tile.
 *
 * <p>Tokens are compared as {@code int} ids from a shared dictionary, so equality is exact; the rolling hash is only
 * used to find candidates and every candidate is verified.
 */
public final class GreedyStringTiling {

    public record Tile(int startA, int startB, int length) {
    }

    private record Match(int a, int b, int len) {
    }

    private static final long BASE = 0x9E3779B97F4A7C15L;

    private GreedyStringTiling() {
    }

    public static List<Tile> tile(int[] a, int[] b, boolean[] preMarkedA, boolean[] preMarkedB, int minMatch) {
        int n = a.length, m = b.length;
        List<Tile> tiles = new ArrayList<>();
        if (minMatch < 1 || n < minMatch || m < minMatch) return tiles;
        boolean[] markA = preMarkedA == null ? new boolean[n] : preMarkedA.clone();
        boolean[] markB = preMarkedB == null ? new boolean[m] : preMarkedB.clone();

        int s = Math.max(minMatch, Math.min(20, Math.min(n, m)));
        boolean stop = false;
        while (!stop) {
            List<Match> matches = new ArrayList<>();
            int lmax = scan(a, b, markA, markB, s, matches);
            if (lmax > 2 * s) {
                s = lmax; // restart the search with the longest match length found
                continue;
            }
            mark(matches, markA, markB, tiles);
            if (s > 2 * minMatch) s = s / 2;
            else if (s > minMatch) s = minMatch;
            else stop = true;
        }
        tiles.sort(Comparator.comparingInt(Tile::startA));
        return tiles;
    }

    private static int scan(int[] a, int[] b, boolean[] markA, boolean[] markB, int s, List<Match> out) {
        int n = a.length, m = b.length;
        if (n < s || m < s) return 0;
        int[] prefA = prefix(markA), prefB = prefix(markB);
        long pow = 1;
        for (int i = 0; i < s - 1; i++) pow *= BASE;

        // primitive chained hash table of all unmarked windows of B: head[slot] -> j -> next[j] ...
        int size = Integer.highestOneBit(Math.max(16, m * 2)) << 1;
        int mask = size - 1;
        int[] head = new int[size];
        java.util.Arrays.fill(head, -1);
        int[] next = new int[m];
        long[] winHash = new long[m];
        long h = 0;
        for (int i = 0; i < s; i++) h = h * BASE + a0(b[i]);
        for (int j = 0; j + s <= m; j++) {
            if (j > 0) h = (h - a0(b[j - 1]) * pow) * BASE + a0(b[j + s - 1]);
            if (prefB[j + s] - prefB[j] == 0) {
                int slot = slot(h, mask);
                winHash[j] = h;
                next[j] = head[slot];
                head[slot] = j;
            }
        }

        int lmax = 0;
        h = 0;
        for (int i = 0; i < s; i++) h = h * BASE + a0(a[i]);
        for (int i = 0; i + s <= n; i++) {
            if (i > 0) h = (h - a0(a[i - 1]) * pow) * BASE + a0(a[i + s - 1]);
            if (prefA[i + s] - prefA[i] != 0) continue;
            for (int j = head[slot(h, mask)]; j >= 0; j = next[j]) {
                if (winHash[j] != h || !equalWindow(a, i, b, j, s)) continue;
                // only record maximal-left matches; a match that continues a previous one is found from there
                if (i > 0 && j > 0 && !markA[i - 1] && !markB[j - 1] && a[i - 1] == b[j - 1]) continue;
                int len = s;
                while (i + len < n && j + len < m && a[i + len] == b[j + len] && !markA[i + len] && !markB[j + len]) len++;
                if (len > 2 * s) return len;
                out.add(new Match(i, j, len));
                lmax = Math.max(lmax, len);
            }
        }
        return lmax;
    }

    private static int slot(long h, int mask) {
        return (int) (h ^ (h >>> 32)) & mask;
    }

    private static long a0(int v) {
        return v + 1L;
    }

    private static boolean equalWindow(int[] a, int i, int[] b, int j, int s) {
        for (int x = 0; x < s; x++) {
            if (a[i + x] != b[j + x]) return false;
        }
        return true;
    }

    private static int[] prefix(boolean[] mark) {
        int[] p = new int[mark.length + 1];
        for (int i = 0; i < mark.length; i++) p[i + 1] = p[i] + (mark[i] ? 1 : 0);
        return p;
    }

    private static void mark(List<Match> matches, boolean[] markA, boolean[] markB, List<Tile> tiles) {
        matches.sort((x, y) -> y.len() - x.len());
        for (Match mt : matches) {
            boolean free = true;
            for (int x = 0; x < mt.len() && free; x++) {
                if (markA[mt.a() + x] || markB[mt.b() + x]) free = false;
            }
            if (!free) continue;
            for (int x = 0; x < mt.len(); x++) {
                markA[mt.a() + x] = true;
                markB[mt.b() + x] = true;
            }
            tiles.add(new Tile(mt.a(), mt.b(), mt.len()));
        }
    }
}
