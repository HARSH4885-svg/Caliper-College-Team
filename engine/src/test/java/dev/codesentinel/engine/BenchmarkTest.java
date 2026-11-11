package dev.codesentinel.engine;

import dev.codesentinel.engine.api.AnalysisRequest;
import dev.codesentinel.engine.api.EngineConfig;
import dev.codesentinel.engine.api.SourceFileInput;
import dev.codesentinel.engine.api.SubmissionInput;
import dev.codesentinel.engine.reporting.AnalysisResult;
import dev.codesentinel.engine.similarity.SimilarityEngine;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * NFR-PERF-02: measures (does not assert) the time to compare 100 synthetic submissions of ~300 lines each.
 * Writes target/benchmark.txt with hardware/software details. The only assertion is a generous sanity bound that
 * catches an accidental quadratic blow-up; it is not a performance claim.
 */
class BenchmarkTest {

    /** Worst case: every program has the same statement templates, so nearly all fingerprints are common. */
    static String homogeneousProgram(long seed, int lines) {
        Random r = new Random(seed);
        StringBuilder sb = new StringBuilder("#include <iostream>\nusing namespace std;\n");
        int fn = 0;
        int produced = 2;
        while (produced < lines) {
            int params = 1 + r.nextInt(3);
            StringBuilder sig = new StringBuilder("int fn" + (fn++) + "_" + r.nextInt(1000) + "(");
            for (int p = 0; p < params; p++) sig.append(p > 0 ? ", " : "").append("int p").append(p);
            sb.append(sig).append(") {\n    int acc = ").append(r.nextInt(50)).append(";\n");
            produced += 2;
            int body = 8 + r.nextInt(14);
            for (int i = 0; i < body; i++) {
                int a = r.nextInt(params);
                switch (r.nextInt(6)) {
                    case 0 -> sb.append("    acc = acc + p").append(a).append(" * ").append(r.nextInt(97) + 3).append(";\n");
                    case 1 -> sb.append("    for (int i").append(i).append(" = 0; i").append(i).append(" < ").append(r.nextInt(30) + 3)
                            .append("; i").append(i).append("++) { acc += i").append(i).append(" ^ p").append(a).append("; }\n");
                    case 2 -> sb.append("    if (acc % ").append(r.nextInt(11) + 2).append(" == ").append(r.nextInt(2)).append(") { acc -= ").append(r.nextInt(40))
                            .append("; } else { acc += p").append(a).append(" / ").append(r.nextInt(7) + 1).append("; }\n");
                    case 3 -> sb.append("    while (acc > ").append(r.nextInt(900) + 100).append(") acc = acc / ").append(r.nextInt(3) + 2).append(" - p").append(a).append(";\n");
                    case 4 -> sb.append("    cout << \"v").append(r.nextInt(500)).append("=\" << acc + p").append(a).append(" << endl;\n");
                    default -> sb.append("    acc = (acc << ").append(r.nextInt(4) + 1).append(") | (p").append(a).append(" & ").append(r.nextInt(255)).append(");\n");
                }
                produced++;
            }
            sb.append("    return acc;\n}\n");
            produced += 2;
        }
        sb.append("int main() {\n    cout << fn0_0(1) << endl;\n    return 0;\n}\n");
        return sb.toString();
    }

    private static final String[] BIN = {"+", "-", "*", "/", "%", "&", "|", "^", "<<", ">>"};
    private static final String[] CMP = {"<", ">", "<=", ">=", "==", "!="};

    private static String expr(Random r, int depth, List<String> vars) {
        if (depth == 0 || r.nextInt(4) == 0) {
            return r.nextBoolean() ? vars.get(r.nextInt(vars.size())) : String.valueOf(r.nextInt(2000));
        }
        String e = "(" + expr(r, depth - 1, vars) + " " + BIN[r.nextInt(BIN.length)] + " " + expr(r, depth - 1, vars) + ")";
        return r.nextInt(5) == 0 ? "-" + e : e;
    }

    private static void stmt(Random r, StringBuilder sb, int indent, int depth, List<String> vars, int[] counter) {
        String pad = "    ".repeat(indent);
        int kind = r.nextInt(depth > 2 ? 4 : 9);
        String v = vars.get(r.nextInt(vars.size()));
        switch (kind) {
            case 0, 1 -> sb.append(pad).append(v).append(" = ").append(expr(r, 3, vars)).append(";\n");
            case 2 -> sb.append(pad).append(v).append(r.nextBoolean() ? " += " : " -= ").append(expr(r, 2, vars)).append(";\n");
            case 3 -> sb.append(pad).append(v).append(r.nextBoolean() ? "++" : "--").append(";\n");
            case 4 -> {
                String c = vars.get(r.nextInt(vars.size()));
                sb.append(pad).append("if (").append(expr(r, 1, vars)).append(' ').append(CMP[r.nextInt(CMP.length)]).append(' ').append(expr(r, 1, vars)).append(") {\n");
                for (int i = 0, n = 1 + r.nextInt(3); i < n; i++) stmt(r, sb, indent + 1, depth + 1, vars, counter);
                if (r.nextBoolean()) {
                    sb.append(pad).append("} else {\n");
                    for (int i = 0, n = 1 + r.nextInt(2); i < n; i++) stmt(r, sb, indent + 1, depth + 1, vars, counter);
                }
                sb.append(pad).append("}\n");
                assert c != null;
            }
            case 5 -> {
                String i = "k" + (counter[0]++);
                sb.append(pad).append("for (int ").append(i).append(" = ").append(r.nextInt(3)).append("; ").append(i).append(' ').append(CMP[r.nextInt(2)])
                        .append(' ').append(expr(r, 1, vars)).append("; ").append(i).append(r.nextBoolean() ? "++" : " += 2").append(") {\n");
                vars.add(i);
                for (int j = 0, n = 1 + r.nextInt(3); j < n; j++) stmt(r, sb, indent + 1, depth + 1, vars, counter);
                vars.remove(vars.size() - 1);
                sb.append(pad).append("}\n");
            }
            case 6 -> {
                sb.append(pad).append("while (").append(expr(r, 1, vars)).append(' ').append(CMP[r.nextInt(CMP.length)]).append(' ').append(r.nextInt(500)).append(") {\n");
                for (int j = 0, n = 1 + r.nextInt(3); j < n; j++) stmt(r, sb, indent + 1, depth + 1, vars, counter);
                sb.append(pad).append("}\n");
            }
            case 7 -> sb.append(pad).append("switch (").append(v).append(" % ").append(2 + r.nextInt(4)).append(") { case 0: ").append(v).append(" = ")
                    .append(expr(r, 1, vars)).append("; break; default: ").append(v).append(" = ").append(expr(r, 1, vars)).append("; }\n");
            default -> sb.append(pad).append("cout << ").append(expr(r, 2, vars)).append(" << endl;\n");
        }
    }

    /** Realistic workload: statement shapes, nesting, operators and constants vary, so most k-grams are unique. */
    static String diverseProgram(long seed, int lines) {
        Random r = new Random(seed);
        StringBuilder sb = new StringBuilder("#include <iostream>\nusing namespace std;\n");
        int[] counter = {0};
        int fn = 0;
        while (sb.chars().filter(c -> c == '\n').count() < lines) {
            int params = 1 + r.nextInt(3);
            List<String> vars = new ArrayList<>();
            StringBuilder sig = new StringBuilder("int f" + (fn++) + "(");
            for (int p = 0; p < params; p++) {
                sig.append(p > 0 ? ", " : "").append("int p").append(p);
                vars.add("p" + p);
            }
            sb.append(sig).append(") {\n    int acc = ").append(r.nextInt(9)).append(";\n");
            vars.add("acc");
            for (int i = 0, n = 6 + r.nextInt(8); i < n; i++) stmt(r, sb, 1, 0, vars, counter);
            sb.append("    return acc;\n}\n");
        }
        sb.append("int main() {\n    cout << f0(1, 2, 3) << endl;\n    return 0;\n}\n");
        return sb.toString();
    }

    @Test
    void measureHundredSubmissionsOfAboutThreeHundredLines() throws IOException {
        run("diverse (realistic) workload", BenchmarkTest::diverseProgram, true);
    }

    @Test
    void measureHomogeneousWorstCaseWorkload() throws IOException {
        run("homogeneous worst-case workload", BenchmarkTest::homogeneousProgram, false);
    }

    private void run(String label, java.util.function.BiFunction<Long, Integer, String> gen, boolean expectPlantedFound) throws IOException {
        List<SubmissionInput> subs = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            String src = gen.apply(1000L + i, 300);
            subs.add(new SubmissionInput(String.format("S%03d", i), List.of(new SourceFileInput("main.cpp", src))));
        }
        // plant 3 copies so there is something to find
        subs.set(10, new SubmissionInput("S010", subs.get(5).files()));
        subs.set(40, new SubmissionInput("S040", subs.get(33).files()));
        subs.set(77, new SubmissionInput("S077", subs.get(60).files()));

        int lineCount = subs.get(0).files().get(0).content().split("\n").length;
        SimilarityEngine engine = new SimilarityEngine();
        engine.analyze(new AnalysisRequest(List.of(), subs, EngineConfig.defaults())); // warm-up (JIT)
        long best = Long.MAX_VALUE;
        AnalysisResult res = null;
        for (int run = 0; run < 3; run++) {
            long t0 = System.nanoTime();
            res = engine.analyze(new AnalysisRequest(List.of(), subs, EngineConfig.defaults()));
            best = Math.min(best, (System.nanoTime() - t0) / 1_000_000);
        }
        long flagged = res.pairs().stream().filter(p -> p.state().name().endsWith("REVIEW")).count();
        String report = label.toUpperCase() + "\n" + String.format("""
                Benchmark (synthetic): 100 submissions, ~%d lines each (first program), default config
                Best of 3 timed runs after 1 warm-up: %d ms (engine-reported last run: %d ms)
                Possible pairs: %d, candidate pairs after index+Dice filter: %d, pairs flagged for review: %d (3 planted copies)
                Java %s (%s), OS %s %s, CPUs %d, max heap %d MB
                NOTE: target is < 5000 ms; this is a measurement on this machine, not a guarantee.
                """, lineCount, best, res.stats().elapsedMillis(), res.stats().possiblePairs(), res.stats().candidatePairs(), flagged,
                System.getProperty("java.version"), System.getProperty("java.vm.name"), System.getProperty("os.name"),
                System.getProperty("os.arch"), Runtime.getRuntime().availableProcessors(), Runtime.getRuntime().maxMemory() / (1024 * 1024));
        Files.createDirectories(Path.of("target"));
        Path out = Path.of("target", "benchmark.txt");
        String prev = Files.exists(out) && !Files.readString(out).startsWith(label.toUpperCase()) ? Files.readString(out) + "\n" : "";
        Files.writeString(out, prev + report);
        System.out.println(report);
        assertTrue(best < 120_000, "sanity bound only");
        if (expectPlantedFound) assertTrue(flagged >= 3, "planted copies should be flagged, got " + flagged);
    }
}
