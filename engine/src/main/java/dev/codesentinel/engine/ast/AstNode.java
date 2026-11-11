package dev.codesentinel.engine.ast;

import java.util.List;

/**
 * Normalized structural representation of an AST node with trivia, variable names, and literals abstracted.
 */
public final class AstNode {
    private final String type;
    private final String file;
    private final int startLine;
    private final int endLine;
    private final int startCol;
    private final int endCol;
    private final long structuralHash;
    private final int subtreeSize;
    private final List<AstNode> children;

    public AstNode(String type, String file, int startLine, int endLine, int startCol, int endCol,
                   long structuralHash, int subtreeSize, List<AstNode> children) {
        this.type = type;
        this.file = file;
        this.startLine = startLine;
        this.endLine = endLine;
        this.startCol = startCol;
        this.endCol = endCol;
        this.structuralHash = structuralHash;
        this.subtreeSize = subtreeSize;
        this.children = children != null ? children : List.of();
    }

    public String type() { return type; }
    public String file() { return file; }
    public int startLine() { return startLine; }
    public int endLine() { return endLine; }
    public int startCol() { return startCol; }
    public int endCol() { return endCol; }
    public long structuralHash() { return structuralHash; }
    public int subtreeSize() { return subtreeSize; }
    public List<AstNode> children() { return children; }

    @Override
    public String toString() {
        return type + "[" + startLine + ":" + startCol + "-" + endLine + ":" + endCol + " size=" + subtreeSize + "]";
    }
}
