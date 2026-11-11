package dev.codesentinel.judge.compiler;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

final class Files {
    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9_][A-Za-z0-9_./-]*");

    private Files() {
    }

    /** Rejects anything that is not a plain relative path made of safe characters (no "..", no leading '/' or '-'). */
    static String validateName(String name) throws BuildException {
        if (name == null || !SAFE.matcher(name).matches() || name.contains("..") || name.contains("//") || name.length() > 200) {
            throw new BuildException("Unsafe file name rejected: " + (name == null ? "null" : name.replaceAll("[^\\x20-\\x7e]", "?")));
        }
        return name;
    }

    static List<String> withExtension(List<String> files, java.util.Set<String> exts) throws BuildException {
        List<String> out = new ArrayList<>();
        for (String f : files) {
            validateName(f);
            String lower = f.toLowerCase(java.util.Locale.ROOT);
            for (String e : exts) {
                if (lower.endsWith(e)) {
                    out.add("src/" + f);
                    break;
                }
            }
        }
        return out;
    }
}
