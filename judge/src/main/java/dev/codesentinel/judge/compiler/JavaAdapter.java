package dev.codesentinel.judge.compiler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** javac + java. Finds the class declaring {@code public static void main(String[])}, honouring a package statement. */
public final class JavaAdapter implements LanguageAdapter {
    private static final Pattern MAIN = Pattern.compile("public\\s+static\\s+void\\s+main\\s*\\(\\s*(final\\s+)?String\\s*(\\[\\s*\\]\\s*\\w+|\\.\\.\\.\\s*\\w+|\\w+\\s*\\[\\s*\\])\\s*\\)");
    private static final Pattern PKG = Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;");
    private static final Pattern CLS = Pattern.compile("(?:class|record|enum|interface)\\s+(\\w+)");

    private final String image;

    public JavaAdapter(String image) {
        this.image = image;
    }

    @Override
    public String id() {
        return "java";
    }

    @Override
    public String image() {
        return image;
    }

    @Override
    public Set<String> extensions() {
        return Set.of(".java");
    }

    @Override
    public BuildPlan plan(List<String> files, Path srcDir, int memoryMb, long timeLimitMs) throws BuildException {
        List<String> sources = Files.withExtension(files, Set.of(".java"));
        if (sources.isEmpty()) throw new BuildException("No .java source file was submitted.");
        String mainClass = null;
        List<String> candidates = new ArrayList<>();
        for (String s : sources) {
            String text;
            try {
                text = java.nio.file.Files.readString(srcDir.resolve(s.substring("src/".length())), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new BuildException("Cannot read " + s);
            }
            String stripped = stripComments(text);
            if (!MAIN.matcher(stripped).find()) continue;
            Matcher pkg = PKG.matcher(stripped);
            String prefix = pkg.find() ? pkg.group(1) + "." : "";
            String fileBase = s.substring(s.lastIndexOf('/') + 1).replace(".java", "");
            Matcher c = CLS.matcher(stripped);
            String cls = null;
            while (c.find()) {
                if (c.group(1).equals(fileBase)) {
                    cls = fileBase;
                    break;
                }
                if (cls == null) cls = c.group(1);
            }
            if (cls != null) candidates.add(prefix + cls);
        }
        if (candidates.isEmpty()) throw new BuildException("No class with public static void main(String[] args) was found.");
        mainClass = candidates.stream().filter(n -> n.endsWith("Main")).findFirst().orElse(candidates.get(0));
        List<String> compile = new ArrayList<>(List.of("javac", "-encoding", "UTF-8", "-d", "out", "-J-Xmx384m", "-J-XX:+UseSerialGC", "-J-XX:-UsePerfData"));
        compile.addAll(sources);
        int heap = Math.max(8, (int) (memoryMb * 0.7));
        List<String> run = List.of("java", "-Xmx" + heap + "m", "-Xss16m", "-XX:+UseSerialGC", "-XX:-UsePerfData",
                "-XX:TieredStopAtLevel=1", "-Djava.io.tmpdir=/tmp", "-cp", "out", mainClass);
        return new BuildPlan(compile, run);
    }

    private static String stripComments(String s) {
        return s.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)//.*$", " ");
    }
}
