package dev.codesentinel.judge.compiler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/** Python 3 and Node.js. A syntax check runs first (reported as CE); no package installation ever happens. */
public final class ScriptAdapter implements LanguageAdapter {
    private final String id;
    private final String image;
    private final Set<String> exts;
    private final List<String> preferredNames;

    private ScriptAdapter(String id, String image, Set<String> exts, List<String> preferredNames) {
        this.id = id;
        this.image = image;
        this.exts = exts;
        this.preferredNames = preferredNames;
    }

    public static ScriptAdapter python(String image) {
        return new ScriptAdapter("python", image, Set.of(".py"), List.of("main.py", "solution.py", "app.py"));
    }

    public static ScriptAdapter javascript(String image) {
        return new ScriptAdapter("javascript", image, Set.of(".js", ".mjs"), List.of("main.js", "index.js", "solution.js", "app.js"));
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
        String entry = null;
        for (String pref : preferredNames) {
            for (String s : sources) {
                if (s.equals("src/" + pref)) entry = s;
            }
        }
        if (entry == null && sources.size() == 1) entry = sources.get(0);
        if (entry == null && id.equals("python")) {
            for (String s : sources) {
                try {
                    String t = java.nio.file.Files.readString(srcDir.resolve(s.substring(4)), StandardCharsets.UTF_8);
                    if (t.contains("__name__")) {
                        entry = s;
                        break;
                    }
                } catch (IOException ignored) {
                    // fall through to error below
                }
            }
        }
        if (entry == null) {
            throw new BuildException("Cannot determine the entry file. Name it " + preferredNames.get(0) + " or submit a single file.");
        }
        if (id.equals("python")) {
            List<String> check = new java.util.ArrayList<>(List.of("python3", "-I", "-c",
                    "import ast,sys\nfor f in sys.argv[1:]:\n    ast.parse(open(f, encoding='utf-8').read(), f)"));
            check.addAll(sources);
            return new BuildPlan(check, List.of("python3", "-B", entry));
        }
        List<String> check = new java.util.ArrayList<>(List.of("node", "--check", entry));
        return new BuildPlan(check, List.of("node", "--max-old-space-size=" + Math.max(16, (int) (memoryMb * 0.7)), entry));
    }
}
