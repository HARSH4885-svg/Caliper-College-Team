package dev.codesentinel.engine.reporting;

import dev.codesentinel.engine.api.EngineConfig;

import java.util.List;

public record AnalysisResult(String algorithmVersion, EngineConfig config, List<PairReport> pairs,
                             List<SubmissionNote> notes, List<FileFingerprints> fingerprints, AnalysisStats stats) {
    public static final String DISCLAIMER =
            "Similarity is an investigation signal, not a verdict. Only the instructor can record an academic decision.";
}
