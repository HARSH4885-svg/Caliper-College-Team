package dev.codesentinel.engine.similarity;

import dev.codesentinel.engine.api.EngineConfig;
import dev.codesentinel.engine.ast.AstStructuralFragment;
import dev.codesentinel.engine.ast.AstStructuralResult;
import dev.codesentinel.engine.reporting.Fragment;
import dev.codesentinel.engine.reporting.PairReport;
import dev.codesentinel.engine.reporting.ReportState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Stage 2 for one candidate pair: structural verification with RKR-GST, rarity weighting, fragment/line mapping and
 * the triage decision. Produces a fully explained {@link PairReport}.
 */
final class PairAnalyzer {

    /** Document frequencies and the weighting rule shared by every pair of one language group. */
    static final class Rarity {
        final int n;
        final boolean active;
        final Map<Long, Integer> fingerprintDf;
        final Map<Long, Integer> kgramDf;

        Rarity(int n, int minSubmissions, Map<Long, Integer> fingerprintDf, Map<Long, Integer> kgramDf) {
            this.n = n;
            this.active = n >= minSubmissions;
            this.fingerprintDf = fingerprintDf;
            this.kgramDf = kgramDf;
        }

        /** 1.0 for a hash seen in one submission, approaching 0 as it appears in more of them. */
        double weight(int df) {
            if (!active) return 1.0;
            int d = Math.max(1, Math.min(df, n));
            return Math.log((n + 1.0) / d) / Math.log(n + 1.0);
        }
    }

    private final EngineConfig cfg;

    PairAnalyzer(EngineConfig cfg) {
        this.cfg = cfg;
    }

    /**
     * Cheap rarity-weighted Dice used to keep pairs that share ONLY common fingerprints away from the expensive
     * structural stage (they could never reach "needs review" anyway).
     */
    double weightedDice(SubmissionProfile a, SubmissionProfile b, Rarity rarity) {
        Set<Long> small = a.fingerprintSet.size() <= b.fingerprintSet.size() ? a.fingerprintSet : b.fingerprintSet;
        Set<Long> large = small == a.fingerprintSet ? b.fingerprintSet : a.fingerprintSet;
        double sharedW = 0;
        for (long h : small) {
            if (large.contains(h)) sharedW += rarity.weight(rarity.fingerprintDf.getOrDefault(h, 2));
        }
        double total = totalWeight(a.fingerprintSet, rarity) + totalWeight(b.fingerprintSet, rarity);
        return total == 0 ? 0 : 2 * sharedW / total;
    }

    PairReport analyze(SubmissionProfile a, SubmissionProfile b, Rarity rarity) {
        return analyze(a, b, rarity, null);
    }

    PairReport analyze(SubmissionProfile a, SubmissionProfile b, Rarity rarity, AstStructuralResult astResult) {
        List<String> why = new ArrayList<>();

        // ---- stage 1 metrics --------------------------------------------------------------------------------------
        Set<Long> small = a.fingerprintSet.size() <= b.fingerprintSet.size() ? a.fingerprintSet : b.fingerprintSet;
        Set<Long> large = small == a.fingerprintSet ? b.fingerprintSet : a.fingerprintSet;
        int shared = 0;
        double sharedW = 0;
        for (long h : small) {
            if (large.contains(h)) {
                shared++;
                sharedW += rarity.weight(rarity.fingerprintDf.getOrDefault(h, 2));
            }
        }
        double dice = Dice.coefficient(shared, a.fingerprintSet.size(), b.fingerprintSet.size());
        double wa = totalWeight(a.fingerprintSet, rarity), wb = totalWeight(b.fingerprintSet, rarity);
        double weightedDice = (wa + wb) == 0 ? 0 : 2 * sharedW / (wa + wb);

        // ---- stage 2: structural verification ---------------------------------------------------------------------
        List<GreedyStringTiling.Tile> tiles = List.of();
        boolean gstSkipped = a.ids.length > cfg.maxGstTokens() || b.ids.length > cfg.maxGstTokens();
        if (!gstSkipped) {
            tiles = GreedyStringTiling.tile(a.ids, b.ids, a.suppressed, b.suppressed, cfg.minMatchLength());
        }

        int matched = 0, distinctive = 0, longest = 0;
        List<Fragment> fragments = new ArrayList<>();
        for (GreedyStringTiling.Tile t : tiles) {
            int inf = 0;
            for (int x = 0; x < t.length(); x++) if (a.informative[t.startA() + x]) inf++;
            double wt = tileWeight(a, t, rarity);
            boolean distinct = wt >= cfg.commonTileWeight();
            matched += inf;
            if (distinct) distinctive += inf;
            longest = Math.max(longest, t.length());
            var fa = a.tokens[t.startA()].origin();
            var la = a.tokens[t.startA() + t.length() - 1].origin();
            var fb = b.tokens[t.startB()].origin();
            var lb = b.tokens[t.startB() + t.length() - 1].origin();
            fragments.add(new Fragment(fa.file(), fa.startLine(), la.endLine(), fb.file(), fb.startLine(), lb.endLine(),
                    t.length(), round(wt), distinct));
        }
        fragments.sort(Comparator.comparingInt(Fragment::tokenCount).reversed());
        if (fragments.size() > 200) fragments = new ArrayList<>(fragments.subList(0, 200));

        int infA = a.informativeCount, infB = b.informativeCount;
        double covA = infA == 0 ? 0 : (double) matched / infA;
        double covB = infB == 0 ? 0 : (double) matched / infB;
        double score = (infA + infB) == 0 ? 0 : 2.0 * distinctive / (infA + infB);
        double commonFraction = matched == 0 ? 0 : (double) distinctive / matched;

        // ---- explanation + triage decision ------------------------------------------------------------------------
        why.add(String.format(Locale.ROOT, "Fingerprint Dice %.2f (weighted %.2f): %d shared of %d / %d fingerprints (k=%d, w=%d).",
                dice, weightedDice, shared, a.fingerprintSet.size(), b.fingerprintSet.size(), cfg.k(), cfg.w()));
        if (gstSkipped) {
            why.add("Structural verification skipped: a submission exceeds " + cfg.maxGstTokens() + " tokens. Evidence is limited to fingerprints.");
        } else {
            why.add(String.format(Locale.ROOT, "%d tile(s) cover %d of %d informative tokens in A (%.0f%%) and %d of %d in B (%.0f%%); longest tile %d tokens.",
                    tiles.size(), matched, infA, covA * 100, matched, infB, covB * 100, longest));
            why.add(String.format(Locale.ROOT, "%d matched tokens lie in distinctive tiles (score %.2f); the rest are common patterns.",
                    distinctive, score));
        }
        if (a.suppressedCount > 0 || b.suppressedCount > 0) {
            why.add("Starter/common code excluded before comparison: " + a.suppressedCount + " token(s) in A, "
                    + b.suppressedCount + " in B.");
        }
        why.add(rarity.active
                ? "Rarity weighting active over " + rarity.n + " submissions: matches that most students share count for less."
                : "Rarity weighting inactive (fewer than " + cfg.rarityMinSubmissions() + " submissions): common idioms cannot be told from distinctive code, so treat with extra caution.");

        ReportState state;
        if (infA < cfg.minInformativeTokens() || infB < cfg.minInformativeTokens()) {
            state = ReportState.INSUFFICIENT_EVIDENCE;
            why.add("Insufficient evidence: a submission has fewer than " + cfg.minInformativeTokens()
                    + " informative tokens, so a similarity percentage would be misleading.");
        } else if (gstSkipped) {
            state = dice >= cfg.reviewThreshold() ? ReportState.NEEDS_REVIEW : ReportState.LOW_SIMILARITY;
        } else if (distinctive >= cfg.minHighTokens() && score >= cfg.highThreshold() && dice >= cfg.highThreshold()
                && (!rarity.active || commonFraction >= 0.7)) {
            state = ReportState.HIGH_PRIORITY_REVIEW;
            why.add(String.format(Locale.ROOT, "High-priority review: distinctive score %.2f and fingerprint Dice %.2f both reach %.2f, with at least %d distinctive tokens and mostly non-common evidence. This is a triage signal, not proof of misconduct.",
                    score, dice, cfg.highThreshold(), cfg.minHighTokens()));
        } else if (distinctive >= cfg.minReviewTokens() && score >= cfg.reviewThreshold()) {
            state = ReportState.NEEDS_REVIEW;
            why.add("Needs review: some distinctive overlap, below the high-priority criteria.");
        } else if (matched >= 20) {
            state = ReportState.LOW_SIMILARITY;
            why.add("Low similarity: the overlap is mostly ordinary or shared patterns.");
        } else {
            state = ReportState.NO_MEANINGFUL_MATCH;
        }

        double astScore = 0.0;
        List<AstStructuralFragment> astFragments = List.of();
        if (astResult != null && astResult.totalSubtreesA() > 0) {
            astScore = astResult.structuralScore();
            astFragments = astResult.matchedFragments();
            why.add(astResult.explanation());
        }

        return new PairReport(a.id, b.id, a.language, state, round(dice), round(weightedDice), shared,
                a.fingerprintSet.size(), b.fingerprintSet.size(), infA, infB, matched, distinctive,
                round(covA), round(covB), round(score), longest, a.suppressedCount, b.suppressedCount, rarity.active,
                fragments, why, astScore, astFragments);
    }

    private double totalWeight(Set<Long> set, Rarity r) {
        double sum = 0;
        for (long h : set) sum += r.weight(r.fingerprintDf.getOrDefault(h, 1));
        return sum;
    }

    /** Mean rarity weight of the valid k-grams inside the tile (positions start .. start+len-k). */
    private double tileWeight(SubmissionProfile a, GreedyStringTiling.Tile t, Rarity r) {
        int last = t.startA() + t.length() - cfg.k();
        double sum = 0;
        int count = 0;
        for (int p = t.startA(); p <= last; p++) {
            if (!a.kgramValid[p]) continue;
            sum += r.weight(r.kgramDf.getOrDefault(a.kgramAt[p], 1));
            count++;
        }
        return count == 0 ? 0 : sum / count;
    }

    private static double round(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}
