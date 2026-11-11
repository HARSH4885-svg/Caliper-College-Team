package dev.codesentinel.submissions;

import java.util.Locale;
import java.util.Set;

/** Which file extensions count as source for each supported language, and which extra text files are tolerated. */
public final class LanguageFiles {
    private LanguageFiles() {
    }

    public static Set<String> sourceExtensions(String language) {
        return switch (language) {
            case "c" -> Set.of("c", "h");
            case "cpp" -> Set.of("cpp", "cc", "cxx", "hpp", "hh", "h");
            case "java" -> Set.of("java");
            case "python" -> Set.of("py");
            case "javascript" -> Set.of("js", "mjs");
            default -> Set.of();
        };
    }

    /** Allowed in an upload at all; anything else (binaries, archives, images) is rejected. */
    public static final Set<String> ALLOWED = Set.of("c", "h", "cpp", "cc", "cxx", "hpp", "hh", "java", "py", "js", "mjs", "txt", "md", "csv", "json");

    public static String ext(String path) {
        int s = path.lastIndexOf('/');
        String leaf = s >= 0 ? path.substring(s + 1) : path;
        int d = leaf.lastIndexOf('.');
        return d < 0 ? "" : leaf.substring(d + 1).toLowerCase(Locale.ROOT);
    }
}
