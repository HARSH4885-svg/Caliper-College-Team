package dev.codesentinel.judge.compiler;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Registry of the five core executable languages. TypeScript/PHP/HTML are intentionally not executed (spec 5.3). */
public final class Adapters {
    public static final String IMAGE_NATIVE = "codesentinel/runner-native:1";
    public static final String IMAGE_JAVA = "codesentinel/runner-java:1";
    public static final String IMAGE_PYTHON = "codesentinel/runner-python:1";
    public static final String IMAGE_NODE = "codesentinel/runner-node:1";

    private static final Map<String, LanguageAdapter> BY_ID = new LinkedHashMap<>();

    static {
        for (LanguageAdapter a : new LanguageAdapter[]{CompiledNativeAdapter.c(IMAGE_NATIVE), CompiledNativeAdapter.cpp(IMAGE_NATIVE),
                new JavaAdapter(IMAGE_JAVA), ScriptAdapter.python(IMAGE_PYTHON), ScriptAdapter.javascript(IMAGE_NODE)}) {
            BY_ID.put(a.id(), a);
        }
    }

    private Adapters() {
    }

    public static Optional<LanguageAdapter> forLanguage(String id) {
        return Optional.ofNullable(id == null ? null : BY_ID.get(id.toLowerCase(java.util.Locale.ROOT)));
    }

    public static java.util.Set<String> supportedLanguages() {
        return BY_ID.keySet();
    }
}
