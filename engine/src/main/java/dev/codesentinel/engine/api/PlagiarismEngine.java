package dev.codesentinel.engine.api;

import dev.codesentinel.engine.reporting.AnalysisResult;

/**
 * The frozen engine contract (Increment 1 checkpoint). The portal depends only on this interface and the records in
 * {@code api} and {@code reporting}. The engine never touches HTTP, JDBC or process execution (NFR-MAINT-02).
 */
public interface PlagiarismEngine {
    AnalysisResult analyze(AnalysisRequest request);
}
