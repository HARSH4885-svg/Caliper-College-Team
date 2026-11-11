package dev.codesentinel.engine.lexer;

import java.util.Locale;
import java.util.Optional;

/** Languages the engine can tokenize. Only same-language profiles are ever compared (FR-PLAG-15). */
public enum Language {
    C, CPP, JAVA, PYTHON, JAVASCRIPT;

    /**
     * Maps a file name to a language by extension. A bare {@code .h} is reported as C; the engine re-assigns it to C++
     * when the same submission also contains C++ sources.
     */
    public static Optional<Language> fromFileName(String fileName) {
        String n = fileName.toLowerCase(Locale.ROOT);
        int slash = Math.max(n.lastIndexOf('/'), n.lastIndexOf('\\'));
        if (slash >= 0) n = n.substring(slash + 1);
        int dot = n.lastIndexOf('.');
        if (dot < 0) return Optional.empty();
        return switch (n.substring(dot + 1)) {
            case "c", "h" -> Optional.of(C);
            case "cpp", "cc", "cxx", "cp", "hpp", "hh", "hxx" -> Optional.of(CPP);
            case "java" -> Optional.of(JAVA);
            case "py" -> Optional.of(PYTHON);
            case "js", "mjs", "cjs" -> Optional.of(JAVASCRIPT);
            default -> Optional.empty();
        };
    }

    public static boolean isBareCHeader(String fileName) {
        return fileName.toLowerCase(Locale.ROOT).endsWith(".h");
    }
}
