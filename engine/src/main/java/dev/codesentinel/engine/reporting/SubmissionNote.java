package dev.codesentinel.engine.reporting;

import dev.codesentinel.engine.lexer.Language;

import java.util.List;

/** Per-submission accounting: how much was analysed, suppressed, and whether it can be scored at all. */
public record SubmissionNote(String submissionId, Language language, int totalTokens, int informativeTokens,
                             int suppressedTokens, int fingerprintCount, boolean insufficientEvidence,
                             List<String> reasons) {
}
