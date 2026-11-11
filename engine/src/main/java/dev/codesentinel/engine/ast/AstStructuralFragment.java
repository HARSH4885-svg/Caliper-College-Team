package dev.codesentinel.engine.ast;

/**
 * Represents a matched structural code fragment between two submissions based on AST subtree analysis.
 */
public record AstStructuralFragment(
        String fileA,
        int startLineA,
        int endLineA,
        String fileB,
        int startLineB,
        int endLineB,
        String nodeType,
        int nodeCount
) {}
