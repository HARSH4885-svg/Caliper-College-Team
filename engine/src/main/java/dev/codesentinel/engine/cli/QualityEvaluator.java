package dev.codesentinel.engine.cli;

import dev.codesentinel.engine.reporting.AnalysisResult;
import dev.codesentinel.engine.reporting.PairReport;
import dev.codesentinel.engine.reporting.ReportState;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Confusion-matrix evaluation against labelled synthetic data (section 41). A pair is "predicted positive" when the
 * given predicate accepts its report; pairs the engine never surfaced are predicted negative. Undefined metrics (zero
 * denominators) are reported as NaN and printed as "undefined" - never invented.
 */
public final class QualityEvaluator {

    public record Confusion(int tp, int fp, int tn, int fn, List<String> falsePositives, List<String> falseNegatives) {
        public double precision() {
            return ratio(tp, tp + fp);
        }

        public double recall() {
            return ratio(tp, tp + fn);
        }

        public double falsePositiveRate() {
            return ratio(fp, fp + tn);
        }

        static double ratio(int num, int den) {
            return den == 0 ? Double.NaN : (double) num / den;
        }

        public static String fmt(double v) {
            return Double.isNaN(v) ? "undefined" : String.format(Locale.ROOT, "%.3f", v);
        }

        @Override
        public String toString() {
            return String.format(Locale.ROOT, "TP=%d FP=%d TN=%d FN=%d precision=%s recall=%s FPR=%s",
                    tp, fp, tn, fn, fmt(precision()), fmt(recall()), fmt(falsePositiveRate()));
        }
    }

    private QualityEvaluator() {
    }

    /** Positive predicates used in the reports. */
    public static final Predicate<PairReport> ANY_FLAG =
            r -> r.state() == ReportState.HIGH_PRIORITY_REVIEW || r.state() == ReportState.NEEDS_REVIEW;
    public static final Predicate<PairReport> HIGH_ONLY = r -> r.state() == ReportState.HIGH_PRIORITY_REVIEW;

    public static Confusion evaluate(AnalysisResult result, List<String> submissionIds, Set<String> truthPairs,
                                     Predicate<PairReport> positive) {
        Set<String> predicted = new HashSet<>();
        for (PairReport r : result.pairs()) {
            if (positive.test(r)) predicted.add(key(r.submissionA(), r.submissionB()));
        }
        int tp = 0, fp = 0, tn = 0, fn = 0;
        List<String> fps = new java.util.ArrayList<>(), fns = new java.util.ArrayList<>();
        for (int i = 0; i < submissionIds.size(); i++) {
            for (int j = i + 1; j < submissionIds.size(); j++) {
                String k = key(submissionIds.get(i), submissionIds.get(j));
                boolean truth = truthPairs.contains(k);
                boolean pred = predicted.contains(k);
                if (truth && pred) tp++;
                else if (!truth && pred) {
                    fp++;
                    fps.add(k);
                } else if (truth) {
                    fn++;
                    fns.add(k);
                } else tn++;
            }
        }
        return new Confusion(tp, fp, tn, fn, fps, fns);
    }

    public static String key(String a, String b) {
        return a.compareTo(b) <= 0 ? a + "|" + b : b + "|" + a;
    }
}
