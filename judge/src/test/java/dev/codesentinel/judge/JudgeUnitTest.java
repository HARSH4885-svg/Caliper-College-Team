package dev.codesentinel.judge;

import dev.codesentinel.judge.compiler.Adapters;
import dev.codesentinel.judge.compiler.BuildException;
import dev.codesentinel.judge.sandbox.ContainerRunner;
import dev.codesentinel.judge.sandbox.ContainerSpec;
import dev.codesentinel.judge.sandbox.DockerRunner;
import dev.codesentinel.judge.sandbox.RunResult;
import dev.codesentinel.judge.sandbox.SandboxException;
import dev.codesentinel.judge.testcase.OutputComparator;
import dev.codesentinel.judge.testcase.TestCase;
import dev.codesentinel.judge.verdict.JudgeResult;
import dev.codesentinel.judge.verdict.TestCaseResult;
import dev.codesentinel.judge.verdict.Verdict;
import dev.codesentinel.judge.verdict.VerdictClassifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** No Docker needed: comparison policy, verdict rules, adapters and orchestration with a fake runner. */
class JudgeUnitTest {

    // ---- output normalization policy (FR-JUDGE-07) -----------------------------------------------------------------

    @Test
    void crlfAndTrailingWhitespaceAndTrailingBlankLinesAreIgnored() {
        assertTrue(OutputComparator.matches("1 2\n3\n", "1 2  \r\n3\t\r\n\r\n\r\n"));
        assertTrue(OutputComparator.matches("", "\n\n"));
        assertTrue(OutputComparator.matches("a", "a\n"));
    }

    @Test
    void meaningfulDifferencesAreNeverIgnored() {
        assertFalse(OutputComparator.matches("1 2", "2 1"), "token order");
        assertFalse(OutputComparator.matches("1", "1.0"), "numeric representation");
        assertFalse(OutputComparator.matches("a b", "a  b"), "inner spacing");
        assertFalse(OutputComparator.matches("a\nb", "a\n\nb"), "blank line in the middle");
        assertFalse(OutputComparator.matches(" a", "a"), "leading whitespace");
        assertFalse(OutputComparator.matches("Yes", "yes"), "case");
        assertFalse(OutputComparator.matches("3", "3,"), "punctuation");
    }

    // ---- verdict classification -----------------------------------------------------------------------------------

    private static RunResult rr(int exit, String out, String err, boolean timedOut, boolean oom, boolean outLimit) {
        return new RunResult(exit, out, err, timedOut, oom, outLimit, 10);
    }

    @Test
    void verdictRules() {
        assertEquals(Verdict.AC, VerdictClassifier.classify(rr(0, "6\n", "", false, false, false), "6").verdict());
        assertEquals(Verdict.WA, VerdictClassifier.classify(rr(0, "7\n", "", false, false, false), "6").verdict());
        assertEquals(Verdict.RE, VerdictClassifier.classify(rr(139, "", "", false, false, false), "6").verdict());
        assertEquals(Verdict.RE, VerdictClassifier.classify(rr(1, "", "Traceback", false, false, false), "6").verdict());
        assertEquals(Verdict.TLE, VerdictClassifier.classify(rr(124, "", "", false, false, false), "6").verdict());
        assertEquals(Verdict.TLE, VerdictClassifier.classify(rr(-1, "", "", true, false, false), "6").verdict());
        assertEquals(Verdict.MLE, VerdictClassifier.classify(rr(137, "", "", false, true, false), "6").verdict(), "OOM beats the 137 exit code");
        assertEquals(Verdict.MLE, VerdictClassifier.classify(rr(1, "", "Exception in thread main java.lang.OutOfMemoryError: Java heap space", false, false, false), "6").verdict());
        assertEquals(Verdict.RE, VerdictClassifier.classify(rr(0, "xxxx", "", false, false, true), "6").verdict());
    }

    @Test
    void aggregateIsFirstFailureAndInternalErrorWins() {
        TestCaseResult ac = new TestCaseResult("1", true, Verdict.AC, 1, "", "", "");
        TestCaseResult wa = new TestCaseResult("2", false, Verdict.WA, 1, "", "", "");
        TestCaseResult tle = new TestCaseResult("3", false, Verdict.TLE, 1, "", "", "");
        TestCaseResult ie = new TestCaseResult("4", false, Verdict.IE, 1, "", "", "");
        assertEquals(Verdict.AC, VerdictClassifier.aggregate(List.of(ac, ac)));
        assertEquals(Verdict.WA, VerdictClassifier.aggregate(List.of(ac, wa, tle)));
        assertEquals(Verdict.IE, VerdictClassifier.aggregate(List.of(ac, wa, ie)));
        assertEquals(Verdict.IE, VerdictClassifier.aggregate(List.of()));
    }

    // ---- adapters ----------------------------------------------------------------------------------------------------

    @Test
    void javaAdapterFindsMainClassWithPackageAndIgnoresCommentedMain(@TempDir Path dir) throws Exception {
        Files.createDirectories(dir.resolve("com/x"));
        Files.writeString(dir.resolve("com/x/Helper.java"), "package com.x;\n// public static void main(String[] a) {}\nclass Helper {}\n");
        Files.writeString(dir.resolve("com/x/Main.java"), "package com.x;\npublic class Main { public static void main(String[] args) { } }\n");
        var plan = Adapters.forLanguage("java").orElseThrow().plan(List.of("com/x/Helper.java", "com/x/Main.java"), dir, 256, 2000);
        assertEquals("com.x.Main", plan.runCommand().get(plan.runCommand().size() - 1));
        assertTrue(plan.compileCommand().contains("src/com/x/Helper.java"));
    }

    @Test
    void javaWithoutMainIsABuildFailure(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("A.java"), "public class A { }");
        assertThrows(BuildException.class, () -> Adapters.forLanguage("java").orElseThrow().plan(List.of("A.java"), dir, 256, 2000));
    }

    @Test
    void unsafeFileNamesAreRejectedBeforeAnyCommandIsBuilt(@TempDir Path dir) {
        for (String bad : List.of("../evil.c", "/etc/passwd.c", "a b.c", "a;rm -rf x.c", "-O2.c", "x$(id).c", "a\n.c")) {
            assertThrows(BuildException.class, () -> Adapters.forLanguage("c").orElseThrow().plan(List.of(bad), dir, 256, 2000), bad);
        }
    }

    @Test
    void pythonEntryDetection(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("a.py"), "print(1)");
        Files.writeString(dir.resolve("b.py"), "print(2)");
        assertThrows(BuildException.class, () -> Adapters.forLanguage("python").orElseThrow().plan(List.of("a.py", "b.py"), dir, 256, 2000));
        Files.writeString(dir.resolve("b.py"), "if __name__ == '__main__':\n    print(2)");
        var plan = Adapters.forLanguage("python").orElseThrow().plan(List.of("a.py", "b.py"), dir, 256, 2000);
        assertEquals("src/b.py", plan.runCommand().get(plan.runCommand().size() - 1));
    }

    @Test
    void onlyTheFiveCoreLanguagesAreExecutable() {
        assertEquals(java.util.Set.of("c", "cpp", "java", "python", "javascript"), Adapters.supportedLanguages());
        assertTrue(Adapters.forLanguage("php").isEmpty());
        assertTrue(Adapters.forLanguage("html").isEmpty());
    }

    // ---- container restrictions are really on the command line -----------------------------------------------------

    @Test
    void dockerCommandCarriesEveryRestrictionAndMountsOnlyTheJobDirectory(@TempDir Path dir) throws Exception {
        DockerRunner r = new DockerRunner("docker");
        Method m = DockerRunner.class.getDeclaredMethod("buildCommand", ContainerSpec.class, String.class);
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<String> cmd = (List<String>) m.invoke(r, new ContainerSpec("img:1", List.of("prog"), new byte[0], dir, false, 128, 1.0, 50, 5000, 65536, 16, Map.of("HOME", "/tmp")), "cs-run-x");
        String joined = String.join(" ", cmd);
        for (String must : List.of("--network none", "--read-only", "--cap-drop ALL", "--security-opt no-new-privileges", "--pids-limit 50",
                "--memory 128m", "--memory-swap 128m", "--cpus 1.0", "--user 65534:65534", "--tmpfs /tmp:rw,noexec,nosuid,size=16m", ":/work:ro")) {
            assertTrue(joined.contains(must), "missing " + must + " in: " + joined);
        }
        assertFalse(joined.contains("--privileged"));
        assertFalse(joined.contains("docker.sock"));
        assertFalse(joined.contains("--rm"), "container is inspected for OOM before explicit removal");
        assertEquals(1, cmd.stream().filter(s -> s.equals("-v")).count(), "exactly one mount");
        assertFalse(joined.contains("/home"));
    }

    @Test
    void unsafeCharactersInSubmittedNamesAreMappedAndStayUnique() {
        var m = Judge.safeNames(List.of("array_statistics(1).py", "My Solution.py", "My_Solution.py", "-rf.py", "../x.py"));
        assertEquals("array_statistics_1_.py", m.get("array_statistics(1).py"));
        assertEquals("My_Solution.py", m.get("My Solution.py"));
        assertEquals("My_Solution_2.py", m.get("My_Solution.py"), "collisions get a suffix");
        assertTrue(m.values().stream().noneMatch(n -> n.startsWith("-") || n.contains("..") || n.contains(" ")));
    }

    // ---- orchestration with a fake runner --------------------------------------------------------------------------

    static final class FakeRunner implements ContainerRunner {
        boolean available = true;
        boolean imageExists = true;
        final List<ContainerSpec> specs = new ArrayList<>();
        java.util.function.Function<ContainerSpec, RunResult> behaviour = s -> new RunResult(0, "6\n", "", false, false, false, 5);
        SandboxException failWith;

        public boolean available() {
            return available;
        }

        public boolean imageExists(String image) {
            return imageExists;
        }

        public RunResult run(ContainerSpec spec) throws SandboxException {
            specs.add(spec);
            if (failWith != null) throw failWith;
            return behaviour.apply(spec);
        }
    }

    private JudgeRequest req(Path dir, String lang, String file, List<TestCase> tests) throws IOException {
        Path src = dir.resolve("s");
        Files.createDirectories(src);
        Files.writeString(src.resolve(file), "int main(){return 0;}");
        return new JudgeRequest("j1", lang, src, List.of(file), tests, 2000, 128, 65536);
    }

    private Judge judge(FakeRunner fr, Path dir) {
        return new Judge(fr, new JudgeConfig(dir.resolve("work"), 60_000, 768, 240_000, 4_000, 64, 1.0, 16, 100, 20_000, 16, 1024, 2048));
    }

    @Test
    void dockerUnavailableMeansJudgeUnavailableAndNothingRuns(@TempDir Path dir) throws Exception {
        FakeRunner fr = new FakeRunner();
        fr.available = false;
        JudgeResult r = judge(fr, dir).judge(req(dir, "c", "main.c", List.of(new TestCase("1", "", "6", true))));
        assertEquals(Verdict.IE, r.verdict());
        assertTrue(r.message().contains("Judge unavailable"));
        assertTrue(fr.specs.isEmpty(), "no container - and certainly no host execution - when Docker is down");
    }

    @Test
    void missingImageIsAnInternalErrorNotAStudentVerdict(@TempDir Path dir) throws Exception {
        FakeRunner fr = new FakeRunner();
        fr.imageExists = false;
        assertEquals(Verdict.IE, judge(fr, dir).judge(req(dir, "c", "main.c", List.of(new TestCase("1", "", "6", true)))).verdict());
    }

    @Test
    void compileFailureStopsBeforeAnyTestAndKeepsTheCompilerLog(@TempDir Path dir) throws Exception {
        FakeRunner fr = new FakeRunner();
        fr.behaviour = s -> new RunResult(1, "", "main.c:1: error: expected ';'", false, false, false, 5);
        JudgeResult r = judge(fr, dir).judge(req(dir, "c", "main.c", List.of(new TestCase("1", "", "6", true), new TestCase("2", "", "7", false))));
        assertEquals(Verdict.CE, r.verdict());
        assertTrue(r.compileLog().contains("expected ';'"));
        assertEquals(1, fr.specs.size(), "tests must not run after CE");
        assertTrue(r.results().isEmpty());
    }

    @Test
    void sandboxFailureIsIEAndNeverCrashesTheCaller(@TempDir Path dir) throws Exception {
        FakeRunner fr = new FakeRunner();
        fr.failWith = new SandboxException("docker exploded");
        JudgeResult r = judge(fr, dir).judge(req(dir, "c", "main.c", List.of(new TestCase("1", "", "6", true))));
        assertEquals(Verdict.IE, r.verdict());
    }

    @Test
    void testInputGoesOnStdinOnlyAndRunPhaseMountIsReadOnly(@TempDir Path dir) throws Exception {
        FakeRunner fr = new FakeRunner();
        judge(fr, dir).judge(req(dir, "c", "main.c", List.of(new TestCase("hidden1", "SECRET-INPUT", "6", false))));
        ContainerSpec compile = fr.specs.get(0), run = fr.specs.get(1);
        assertTrue(compile.hostDirWritable());
        assertFalse(run.hostDirWritable());
        assertEquals("SECRET-INPUT", new String(run.stdin()));
        assertFalse(String.join(" ", run.command()).contains("SECRET-INPUT"), "hidden data never appears in argv");
        assertFalse(run.env().toString().contains("SECRET"));
    }

    @Test
    void limitsAreClampedToAdminBounds(@TempDir Path dir) throws Exception {
        FakeRunner fr = new FakeRunner();
        Path src = dir.resolve("s");
        Files.createDirectories(src);
        Files.writeString(src.resolve("main.py"), "print(6)");
        judge(fr, dir).judge(new JudgeRequest("j", "python", src, List.of("main.py"), List.of(new TestCase("1", "", "6", true)), 10_000_000, 99_999, 999_999_999));
        ContainerSpec run = fr.specs.get(fr.specs.size() - 1);
        assertEquals(1024, run.memoryMb());
        assertEquals(20_000 + 4_000, run.wallTimeoutMs());
        assertTrue(run.outputLimitBytes() <= 8L << 20);
    }

    @Test
    void jobDirectoryIsRemovedAfterwards(@TempDir Path dir) throws Exception {
        FakeRunner fr = new FakeRunner();
        judge(fr, dir).judge(req(dir, "c", "main.c", List.of(new TestCase("1", "", "6", true))));
        try (var s = Files.list(dir.resolve("work"))) {
            assertEquals(0, s.count());
        }
    }

    @Test
    void passedCountAndMessageForMixedResults(@TempDir Path dir) throws Exception {
        FakeRunner fr = new FakeRunner();
        int[] n = {0};
        fr.behaviour = s -> n[0]++ == 0 ? new RunResult(0, "", "", false, false, false, 1) /* compile ok */ : new RunResult(0, n[0] == 2 ? "6" : "9", "", false, false, false, 1);
        JudgeResult r = judge(fr, dir).judge(req(dir, "c", "main.c", List.of(new TestCase("a", "", "6", true), new TestCase("b", "", "6", false))));
        assertEquals(Verdict.WA, r.verdict());
        assertEquals(1, r.passed());
        assertEquals(2, r.total());
    }
}
