package dev.codesentinel.engine.lexer;

/**
 * A lexical token with its original position, so normalized token indexes can be mapped back to source lines
 * (FR-PLAG-20). Lines and columns are 1-based; {@code endCol} is exclusive.
 */
public record SourceToken(TokenType type, String text, int startLine, int startCol, int endLine, int endCol,
                          int index, String file) {
}
