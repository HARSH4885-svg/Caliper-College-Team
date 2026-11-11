package dev.codesentinel.engine.normalization;

import dev.codesentinel.engine.lexer.SourceToken;

/** A token after normalization, still pointing at its original token for line mapping. */
public record NormalizedToken(String value, SourceToken origin) {
}
