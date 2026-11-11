package dev.codesentinel.judge.compiler;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** C (gcc, C11) and C++ (g++, C++17) share one adapter. */
public final class CompiledNativeAdapter implements LanguageAdapter {
    private final String id;
    private final String compiler;
    private final String std;
    private final Set<String> exts;
    private final String image;

    private CompiledNativeAdapter(String id, String compiler, String std, Set<String> exts, String image) {
        this.id = id;
        this.compiler = compiler;
        this.std = std;
        this.exts = exts;
        this.image = image;
    }

    public static CompiledNativeAdapter c(String image) {
        return new CompiledNativeAdapter("c", "gcc", "-std=gnu11", Set.of(".c"), image);
    }

    public static CompiledNativeAdapter cpp(String image) {
        return new CompiledNativeAdapter("cpp", "g++", "-std=gnu++17", Set.of(".cpp", ".cc", ".cxx"), image);
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String image() {
        return image;
    }

    @Override
    public Set<String> extensions() {
        return exts;
    }

    @Override
    public BuildPlan plan(List<String> files, Path srcDir, int memoryMb, long timeLimitMs) throws BuildException {
        List<String> sources = Files.withExtension(files, exts);
        if (sources.isEmpty()) throw new BuildException("No " + String.join("/", exts) + " source file was submitted.");
        List<String> cmd = new ArrayList<>(List.of(compiler, "-O2", std, "-pipe", "-o", "out/prog"));
        cmd.addAll(sources);
        cmd.add("-lm");
        return new BuildPlan(cmd, List.of("out/prog"));
    }
}
