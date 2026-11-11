package dev.codesentinel.engine.reporting;

/** A matched region mapped back to original files and 1-based inclusive line ranges (FR-PLAG-20). */
public record Fragment(String fileA, int startLineA, int endLineA, String fileB, int startLineB, int endLineB,
                       int tokenCount, double rarityWeight, boolean distinctive) {
}
