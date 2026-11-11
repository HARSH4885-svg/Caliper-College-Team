package dev.codesentinel.engine.api;

import java.util.List;

/**
 * Everything the engine needs. All submissions must belong to ONE assignment: cross-assignment comparison is out of
 * scope (FR-PLAG-14) and the engine has no notion of other assignments. Instructor starter files may be empty.
 */
public record AnalysisRequest(List<SourceFileInput> starterFiles, List<SubmissionInput> submissions, EngineConfig config) {
}
