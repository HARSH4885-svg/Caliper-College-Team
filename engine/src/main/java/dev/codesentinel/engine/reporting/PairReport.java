package dev.codesentinel.engine.reporting;

import dev.codesentinel.engine.ast.AstStructuralFragment;
import dev.codesentinel.engine.lexer.Language;

import java.util.List;

/**
 * Reproducible evidence for one candidate pair (NFR-ACC-02). {@code submissionA} sorts before {@code submissionB}.
 * Provides dual-engine similarity assessment:
 * 1. Lexical token-level similarity (Winnowing + RKR-GST)
 * 2. Abstract Syntax Tree (AST) structural similarity (Tree-sitter subtree analysis)
 *
 * @param distinctiveScore 2 * distinctiveMatchedTokens / (informativeA + informativeB); the value compared with the
 *                         review/high-priority thresholds. Starter code and common-idiom tiles do not count.
 * @param astStructuralScore AST structural similarity score (0.00 to 1.00), independent of variable names or formatting.
 */
public record PairReport(String submissionA, String submissionB, Language language, ReportState state,
                         double fingerprintDice, double weightedDice, int sharedFingerprints,
                         int fingerprintsA, int fingerprintsB,
                         int informativeTokensA, int informativeTokensB,
                         int matchedTokens, int distinctiveMatchedTokens,
                         double coverageA, double coverageB, double distinctiveScore, int longestTile,
                         int suppressedTokensA, int suppressedTokensB, boolean rarityWeightingActive,
                         List<Fragment> fragments, List<String> explanation,
                         double astStructuralScore, List<AstStructuralFragment> structuralFragments) {

    public PairReport(String submissionA, String submissionB, Language language, ReportState state,
                      double fingerprintDice, double weightedDice, int sharedFingerprints,
                      int fingerprintsA, int fingerprintsB,
                      int informativeTokensA, int informativeTokensB,
                      int matchedTokens, int distinctiveMatchedTokens,
                      double coverageA, double coverageB, double distinctiveScore, int longestTile,
                      int suppressedTokensA, int suppressedTokensB, boolean rarityWeightingActive,
                      List<Fragment> fragments, List<String> explanation) {
        this(submissionA, submissionB, language, state, fingerprintDice, weightedDice, sharedFingerprints,
                fingerprintsA, fingerprintsB, informativeTokensA, informativeTokensB, matchedTokens,
                distinctiveMatchedTokens, coverageA, coverageB, distinctiveScore, longestTile,
                suppressedTokensA, suppressedTokensB, rarityWeightingActive, fragments, explanation,
                0.0, List.of());
    }
}
