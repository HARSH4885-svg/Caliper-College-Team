package dev.codesentinel.engine.lexer;

public enum TokenType {
    KEYWORD, IDENTIFIER, NUMBER, STRING, CHAR, REGEX, OPERATOR, PUNCT,
    /** An include/import/package line collapsed to one token so a normalization policy can suppress common ones. */
    INCLUDE,
    /** Python structure tokens; they are part of the program's structure and are kept in the comparison stream. */
    NEWLINE, INDENT, DEDENT;

    /** True for tokens that carry no program content on their own (used when counting "informative" tokens). */
    public boolean isStructuralOnly() {
        return this == INCLUDE || this == NEWLINE || this == INDENT || this == DEDENT;
    }
}
