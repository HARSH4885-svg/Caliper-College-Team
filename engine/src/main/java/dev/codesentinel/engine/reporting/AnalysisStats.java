package dev.codesentinel.engine.reporting;

import java.util.List;

public record AnalysisStats(int submissions, int languageGroups, long possiblePairs, int candidatePairs,
                            long elapsedMillis, List<String> unsupportedFiles) {
}
