package dev.codesentinel.engine.reporting;

import dev.codesentinel.engine.fingerprint.Fingerprint;
import dev.codesentinel.engine.lexer.Language;

import java.util.List;

/** The winnowed fingerprints of one file, for persistence with {@link AnalysisResult#algorithmVersion()}. */
public record FileFingerprints(String submissionId, String file, Language language, List<Fingerprint> fingerprints) {
}
