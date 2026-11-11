package dev.codesentinel.engine;

import dev.codesentinel.engine.api.AnalysisRequest;
import dev.codesentinel.engine.api.EngineConfig;
import dev.codesentinel.engine.api.SubmissionInput;
import dev.codesentinel.engine.cli.QualityEvaluator;
import dev.codesentinel.engine.cli.QualityEvaluator.Confusion;
import dev.codesentinel.engine.normalization.NormalizationConfig;
import dev.codesentinel.engine.normalization.NormalizationConfig.IdentifierMode;
import dev.codesentinel.engine.normalization.NormalizationConfig.LiteralMode;
import dev.codesentinel.engine.reporting.AnalysisResult;
import dev.codesentinel.engine.reporting.PairReport;
import dev.codesentinel.engine.similarity.SimilarityEngine;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Section 41: measured (never assumed) detection quality on the 17-submission SYNTHETIC set (12 independent
 * solutions + 5 deliberate copies, with ground truth in ground_truth.csv). Writes target/quality-report.md.
 * The assertions are regression guards for this small set only - they are NOT a claim of general fairness.
 */
class QualityMeasurementTest {

    static Set<String> truth() throws IOException {
        Set<String> t = new HashSet<>();
        for (String line : Files.readAllLines(Fixtures.ROOT.resolve("ground_truth.csv"))) {
            if (line.isBlank() || line.startsWith("#")) continue;
            String[] c = line.split(",");
            t.add(QualityEvaluator.key(c[0].trim(), c[1].trim()));
        }
        return t;
    }

    static AnalysisResult run(EngineConfig cfg, boolean starter) {
        List<SubmissionInput> subs = Fixtures.allSubmissionNames().stream().map(Fixtures::sub).toList();
        return new SimilarityEngine().analyze(new AnalysisRequest(starter ? Fixtures.starter() : List.of(), subs, cfg));
    }

    static EngineConfig cfg(IdentifierMode m, double cutoff) {
        return EngineConfig.defaults().withNormalization(new NormalizationConfig(LiteralMode.SMART, true, m)).withCandidateCutoff(cutoff);
    }

    @Test
    void measureAndRecordQualityMatrix() throws IOException {
        Set<String> truth = truth();
        List<String> ids = Fixtures.allSubmissionNames();
        assertEquals(17, ids.size());
        assertEquals(5, truth.size());

        StringBuilder md = new StringBuilder();
        md.append("# Plagiarism engine quality measurement (SYNTHETIC data)\n\n");
        md.append("Dataset: 17 submissions to one assignment = 12 independent solutions (several implement the same textbook algorithms) + 5 deliberate copies. ")
                .append("136 pairs, 5 ground-truth copies, 131 independent pairs. Environment: Java ").append(System.getProperty("java.version"))
                .append(", ").append(System.getProperty("os.name")).append(" ").append(System.getProperty("os.arch")).append(", ")
                .append(Runtime.getRuntime().availableProcessors()).append(" CPU(s).\n\n");
        md.append("Predicted positive = `NEEDS_REVIEW` or `HIGH_PRIORITY_REVIEW` (\"any flag\"); the second column counts only `HIGH_PRIORITY_REVIEW`. ")
                .append("Undefined metrics are shown as `undefined`.\n\n");
        md.append("| identifiers | starter suppression | candidate cutoff | any flag: TP/FP/TN/FN | precision | recall | FPR | high only: TP/FP/FN | candidates |\n|---|---|---|---|---|---|---|---|---|\n");

        for (IdentifierMode m : IdentifierMode.values()) {
            for (boolean starter : new boolean[]{true, false}) {
                for (double cutoff : new double[]{0.5, 0.3}) {
                    AnalysisResult r = run(cfg(m, cutoff), starter);
                    Confusion any = QualityEvaluator.evaluate(r, ids, truth, QualityEvaluator.ANY_FLAG);
                    Confusion high = QualityEvaluator.evaluate(r, ids, truth, QualityEvaluator.HIGH_ONLY);
                    md.append(String.format(Locale.ROOT, "| %s | %s | %.2f | %d/%d/%d/%d | %s | %s | %s | %d/%d/%d | %d |\n", m, starter ? "on" : "off", cutoff,
                            any.tp(), any.fp(), any.tn(), any.fn(), Confusion.fmt(any.precision()), Confusion.fmt(any.recall()),
                            Confusion.fmt(any.falsePositiveRate()), high.tp(), high.fp(), high.fn(), r.stats().candidatePairs()));
                }
            }
        }

        AnalysisResult def = run(EngineConfig.defaults(), true);
        md.append("\n## Default configuration, per surfaced pair\n\nConfig: `").append(def.algorithmVersion()).append("`, cutoff ")
                .append(def.config().candidateCutoff()).append(", review ").append(def.config().reviewThreshold()).append(", high ")
                .append(def.config().highThreshold()).append("\n\n| state | A | B | Dice | weighted Dice | distinctive score | distinctive tokens | ground truth |\n|---|---|---|---|---|---|---|---|\n");
        for (PairReport p : def.pairs()) {
            if (p.state() == dev.codesentinel.engine.reporting.ReportState.NO_MEANINGFUL_MATCH) continue;
            md.append(String.format(Locale.ROOT, "| %s | %s | %s | %.2f | %.2f | %.2f | %d | %s |\n", p.state(), p.submissionA(), p.submissionB(),
                    p.fingerprintDice(), p.weightedDice(), p.distinctiveScore(), p.distinctiveMatchedTokens(),
                    truth.contains(QualityEvaluator.key(p.submissionA(), p.submissionB())) ? "COPY" : "independent"));
        }
        Path out = Path.of("target", "quality-report.md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, md.toString());
        System.out.println(md);

        // regression guard on the DEFAULT configuration, for this synthetic set only
        Confusion any = QualityEvaluator.evaluate(def, ids, truth, QualityEvaluator.ANY_FLAG);
        List<String> fp = new ArrayList<>(any.falsePositives());
        assertEquals(0, any.fp(), "independent solutions flagged: " + fp);
        assertTrue(any.tp() >= 4, "expected at least 4 of 5 deliberate copies surfaced, got " + any);
    }
}
