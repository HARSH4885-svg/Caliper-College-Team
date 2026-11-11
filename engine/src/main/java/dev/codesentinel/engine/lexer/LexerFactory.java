package dev.codesentinel.engine.lexer;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/** Factory pattern: selects the tokenizer for a declared language or a file extension. */
public final class LexerFactory {
    private static final Map<Language, LanguageTokenizer> TOKENIZERS = new EnumMap<>(Language.class);

    static {
        TOKENIZERS.put(Language.C, new CFamilyTokenizer(CFamilyTokenizer.Dialect.C));
        TOKENIZERS.put(Language.CPP, new CFamilyTokenizer(CFamilyTokenizer.Dialect.CPP));
        TOKENIZERS.put(Language.JAVA, new CFamilyTokenizer(CFamilyTokenizer.Dialect.JAVA));
        TOKENIZERS.put(Language.JAVASCRIPT, new CFamilyTokenizer(CFamilyTokenizer.Dialect.JAVASCRIPT));
        TOKENIZERS.put(Language.PYTHON, new PythonTokenizer());
    }

    private LexerFactory() {
    }

    public static LanguageTokenizer forLanguage(Language language) {
        return TOKENIZERS.get(language);
    }

    /** Empty means "unsupported file type" - callers must report it, never guess. */
    public static Optional<LanguageTokenizer> forFileName(String fileName) {
        return Language.fromFileName(fileName).map(TOKENIZERS::get);
    }
}
