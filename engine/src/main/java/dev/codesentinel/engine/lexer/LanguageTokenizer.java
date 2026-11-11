package dev.codesentinel.engine.lexer;

import java.util.List;

/** Strategy interface: one implementation per language (FR-PLAG-01, NFR-MAINT-01). */
public interface LanguageTokenizer {
    Language language();

    /** Produces tokens with comments and formatting whitespace removed. Must never throw on malformed input. */
    List<SourceToken> tokenize(String source, String fileName);
}
