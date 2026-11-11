package dev.codesentinel.engine.api;

import dev.codesentinel.engine.normalization.NormalizationConfig;

/**
 * All tunable engine settings. They are triage settings, not proven universal thresholds (FR-PLAG-23), and are
 * recorded in every report.
 *
 * @param k                    k-gram size in tokens (default 10)
 * @param w                    winnowing window in k-gram hashes (default 5)
 * @param candidateCutoff      Dice cutoff for the stage-1 candidate filter (default 0.30, chosen from the measured quality
 *                             matrix in docs/TEST_PLAN_AND_RESULTS.md; spec suggested ~0.50; NOT an accusation threshold)
 * @param reviewThreshold      distinctive-similarity score from which a pair "needs review" (default 0.40)
 * @param highThreshold        score for "high-priority review" (default 0.80, configurable)
 * @param minInformativeTokens below this a submission cannot be scored reliably -> insufficient evidence
 * @param minReviewTokens      minimum distinctive matched tokens for "needs review"
 * @param minHighTokens        minimum distinctive matched tokens for "high-priority review"
 * @param minMatchLength       minimum tile length for structural matching (must be &gt;= k)
 * @param rarityMinSubmissions rarity weighting needs at least this many submissions in the language group
 * @param commonTileWeight     tiles whose mean rarity weight is below this are "common" (not distinctive evidence)
 * @param maxGstTokens         structural matching is skipped above this stream length (bounds worst-case cost)
 */
public record EngineConfig(int k, int w, double candidateCutoff, double reviewThreshold, double highThreshold,
                           int minInformativeTokens, int minReviewTokens, int minHighTokens, int minMatchLength,
                           int rarityMinSubmissions, double commonTileWeight, int maxGstTokens,
                           NormalizationConfig normalization) {

    public static final String NORMALIZATION_VERSION = "norm-v1";

    public static EngineConfig defaults() {
        return new EngineConfig(10, 5, 0.30, 0.40, 0.80, 50, 40, 100, 10, 5, 0.30, 40_000,
                NormalizationConfig.defaults());
    }

    public EngineConfig {
        if (k < 1) throw new IllegalArgumentException("k must be >= 1");
        if (w < 1) throw new IllegalArgumentException("w must be >= 1");
        if (minMatchLength < k) minMatchLength = k; // tiles must contain at least one k-gram
    }

    public EngineConfig withKW(int newK, int newW) {
        return new EngineConfig(newK, newW, candidateCutoff, reviewThreshold, highThreshold, minInformativeTokens,
                minReviewTokens, minHighTokens, minMatchLength, rarityMinSubmissions, commonTileWeight, maxGstTokens,
                normalization);
    }

    public EngineConfig withCandidateCutoff(double v) {
        return new EngineConfig(k, w, v, reviewThreshold, highThreshold, minInformativeTokens, minReviewTokens,
                minHighTokens, minMatchLength, rarityMinSubmissions, commonTileWeight, maxGstTokens, normalization);
    }

    public EngineConfig withHighThreshold(double v) {
        return new EngineConfig(k, w, candidateCutoff, reviewThreshold, v, minInformativeTokens, minReviewTokens,
                minHighTokens, minMatchLength, rarityMinSubmissions, commonTileWeight, maxGstTokens, normalization);
    }

    public EngineConfig withNormalization(NormalizationConfig n) {
        return new EngineConfig(k, w, candidateCutoff, reviewThreshold, highThreshold, minInformativeTokens,
                minReviewTokens, minHighTokens, minMatchLength, rarityMinSubmissions, commonTileWeight, maxGstTokens, n);
    }

    /** Identifier stored with fingerprints so stale ones can be recomputed after any algorithm/setting change. */
    public String fingerprintVersion() {
        return NORMALIZATION_VERSION + ";k=" + k + ";w=" + w + ";literals=" + normalization.literalMode()
                + ";dropIncludes=" + normalization.dropCommonIncludes()
                + ";identifiers=" + normalization.identifierMode();
    }
}
