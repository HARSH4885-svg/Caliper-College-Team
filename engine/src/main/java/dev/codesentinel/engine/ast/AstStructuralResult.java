package dev.codesentinel.engine.ast;

import java.util.List;

/**
 * Result of AST-based structural similarity comparison between two submissions.
 */
public record AstStructuralResult(
        double structuralScore,
        int sharedSubtrees,
        int totalSubtreesA,
        int totalSubtreesB,
        List<AstStructuralFragment> matchedFragments,
        String explanation
) {
    public static AstStructuralResult empty(String reason) {
        return new AstStructuralResult(0.0, 0, 0, 0, List.of(), reason);
    }
}
