package dev.codesentinel.judge;

import dev.codesentinel.judge.compiler.Adapters;
import dev.codesentinel.judge.compiler.BuildException;
import dev.codesentinel.judge.compiler.BuildPlan;
import dev.codesentinel.judge.compiler.LanguageAdapter;
import dev.codesentinel.judge.sandbox.ContainerRunner;
import dev.codesentinel.judge.sandbox.ContainerSpec;
import dev.codesentinel.judge.sandbox.RunResult;
import dev.codesentinel.judge.sandbox.SandboxException;
import dev.codesentinel.judge.testcase.TestCase;
import dev.codesentinel.judge.verdict.JudgeResult;
import dev.codesentinel.judge.verdict.TestCaseResult;
import dev.codesentinel.judge.verdict.Verdict;
import dev.codesentinel.judge.verdict.VerdictClassifier;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Code judge pipeline (section 15.3): validate -> copy to a job-private directory -> select language adapter ->
 * compile in a container -> run each testcase in its own fresh container -> normalize/compare -> aggregate.
 *
 * <p>Student code is NEVER executed on the host: if the container engine is unavailable the result is IE
 * ("judge unavailable"). This class never calculates similarity (NFR-MAINT-02).
 */
public final class Judge {
    private final ContainerRunner runner;
    private final JudgeConfig cfg;

    public Judge(ContainerRunner runner, JudgeConfig cfg) {
        this.runner = runner;
        this.cfg = cfg;
    }

    public JudgeResult judge(JudgeRequest req) {
        long t0 = System.nanoTime();
        Path jobDir = null;
        try {
            if (!runner.available()) {
                return JudgeResult.internalError("Judge unavailable: the container engine (Docker) is not reachable. Nothing was executed.");
            }
            LanguageAdapter adapter = Adapters.forLanguage(req.language()).orElse(null);
            if (adapter == null) {
                return new JudgeResult(Verdict.IE, "Language '" + req.language() + "' cannot be executed by the judge.", "", 0, req.tests().size(), 0, List.of());
            }
            if (!runner.imageExists(adapter.image())) {
                return JudgeResult.internalError("Runner image " + adapter.image() + " is missing. Build it with scripts/build-sandbox-images.sh.");
            }
            int timeMs = cfg.clampTime(req.timeLimitMs());
            int memMb = cfg.clampMemory(req.memoryLimitMb());
            long outLimit = Math.max(1024, Math.min(req.outputLimitBytes(), 8L << 20));

            java.util.Map<String, String> names = safeNames(req.files());
            jobDir = prepareJobDir(req, names);
            BuildPlan plan;
            try {
                plan = adapter.plan(new ArrayList<>(names.values()), jobDir.resolve("src"), memMb, timeMs);
            } catch (BuildException e) {
                return new JudgeResult(Verdict.CE, e.getMessage(), e.getMessage(), 0, req.tests().size(), 0, List.of());
            }

            // ---- compile --------------------------------------------------------------------------------------
            String compileLog = "";
            if (plan.compileCommand() != null) {
                RunResult c = runner.run(new ContainerSpec(adapter.image(), timeoutWrap(plan.compileCommand(), cfg.compileTimeoutMs() / 1000.0),
                        new byte[0], jobDir, true, cfg.compileMemoryMb(), cfg.cpus(), 128, cfg.compileTimeoutMs() + cfg.startupAllowanceMs(),
                        64 * 1024, 256, Map.of("HOME", "/tmp", "PYTHONDONTWRITEBYTECODE", "1")));
                compileLog = trim((c.stderr() + "\n" + c.stdout()).trim(), 8 * 1024);
                if (c.oomKilled() || c.timedOut() || c.exitCode() != 0) {
                    String why = c.timedOut() || c.exitCode() == 124 || c.exitCode() == 137 ? "Compilation timed out." : "Compilation failed.";
                    return new JudgeResult(Verdict.CE, why, compileLog, 0, req.tests().size(), ms(t0), List.of());
                }
            }

            // ---- run ---------------------------------------------------------------------------------------------
            List<TestCaseResult> results = new ArrayList<>();
            boolean deadlineHit = false;
            for (TestCase tc : req.tests()) {
                if ((System.nanoTime() - t0) / 1_000_000 > cfg.jobTimeoutMs()) {
                    deadlineHit = true;
                    results.add(new TestCaseResult(tc.name(), tc.sample(), Verdict.IE, 0, "Not run: overall job timeout reached.", "", ""));
                    continue;
                }
                RunResult r = runner.run(new ContainerSpec(adapter.image(), timeoutWrap(plan.runCommand(), timeMs / 1000.0),
                        tc.input() == null ? new byte[0] : tc.input().getBytes(StandardCharsets.UTF_8), jobDir, false, memMb, cfg.cpus(),
                        cfg.pidsLimit(), timeMs + cfg.startupAllowanceMs(), outLimit, cfg.tmpfsMb(),
                        Map.of("HOME", "/tmp", "PYTHONDONTWRITEBYTECODE", "1", "PYTHONUNBUFFERED", "1")));
                VerdictClassifier.Classified cl = VerdictClassifier.classify(r, tc.expectedOutput());
                results.add(new TestCaseResult(tc.name(), tc.sample(), cl.verdict(), r.wallMillis(), cl.detail(),
                        trim(r.stdout(), cfg.excerptBytes()), trim(r.stderr(), cfg.excerptBytes())));
            }
            Verdict agg = VerdictClassifier.aggregate(results);
            int passed = (int) results.stream().filter(t -> t.verdict() == Verdict.AC).count();
            String msg = deadlineHit ? "Overall job timeout reached." : agg == Verdict.AC ? "All tests passed." : "First failing verdict: " + agg;
            return new JudgeResult(agg, msg, compileLog, passed, results.size(), ms(t0), results);
        } catch (SandboxException | IOException e) {
            return JudgeResult.internalError("Judge failure: " + e.getMessage());
        } finally {
            if (jobDir != null) deleteQuietly(jobDir);
        }
    }

    private Path prepareJobDir(JudgeRequest req, java.util.Map<String, String> names) throws IOException {
        Files.createDirectories(cfg.workRoot());
        try {
            Files.setPosixFilePermissions(cfg.workRoot(), PosixFilePermissions.fromString("rwx--x--x"));
        } catch (UnsupportedOperationException | IOException ignored) {
            // non-POSIX file system
        }
        Path job = cfg.workRoot().resolve("job-" + UUID.randomUUID());
        Files.createDirectories(job.resolve("src"));
        Files.createDirectories(job.resolve("out"));
        for (java.util.Map.Entry<String, String> en : names.entrySet()) {
            String orig = en.getKey(), f = en.getValue();
            Path from = req.sourceRoot().resolve(orig).normalize();
            if (!from.startsWith(req.sourceRoot().normalize()) || Files.isSymbolicLink(from) || !Files.isRegularFile(from)) {
                continue; // validation of names happens in the adapter; anything odd is simply not copied
            }
            Path to = job.resolve("src").resolve(f).normalize();
            if (!to.startsWith(job.resolve("src"))) continue;
            Files.createDirectories(to.getParent());
            byte[] bytes = Files.readAllBytes(from);
            if (isTextSource(f) && bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
                // Windows editors often save a UTF-8 byte-order mark; javac and Python's syntax check reject it.
                bytes = java.util.Arrays.copyOfRange(bytes, 3, bytes.length);
            }
            Files.write(to, bytes);
        }
        // the container user (nobody) must be able to read sources and write out/
        makeWorldAccessible(job);
        return job;
    }

    /**
     * Browsers and OSes produce names like "array_statistics(1).py" or "My Solution.py". The code never goes through a
     * shell, but the build step only accepts a conservative character set, so each name is mapped to a safe one
     * (unsafe characters become '_') in the job directory only; the stored submission keeps its real name.
     */
    static java.util.Map<String, String> safeNames(List<String> files) {
        java.util.Map<String, String> out = new java.util.LinkedHashMap<>();
        java.util.Set<String> used = new java.util.HashSet<>();
        for (String f : files) {
            String safe = f == null ? "" : f.replaceAll("[^A-Za-z0-9_./-]", "_").replace("..", "_").replaceAll("/+", "/").replaceAll("^[-./]+", "_");
            if (safe.isEmpty()) safe = "_";
            String cand = safe;
            for (int i = 2; !used.add(cand.toLowerCase(java.util.Locale.ROOT)); i++) {
                int dot = safe.lastIndexOf('.');
                cand = dot > safe.lastIndexOf('/') ? safe.substring(0, dot) + "_" + i + safe.substring(dot) : safe + "_" + i;
            }
            out.put(f, cand);
        }
        return out;
    }

    private static boolean isTextSource(String name) {
        String n = name.toLowerCase(java.util.Locale.ROOT);
        for (String e : new String[]{".py", ".java", ".js", ".mjs", ".c", ".h", ".cpp", ".hpp", ".cc", ".cxx"}) {
            if (n.endsWith(e)) return true;
        }
        return false;
    }

    private static void makeWorldAccessible(Path root) throws IOException {
        try (Stream<Path> s = Files.walk(root)) {
            for (Path p : (Iterable<Path>) s::iterator) {
                boolean dir = Files.isDirectory(p);
                Files.setPosixFilePermissions(p, PosixFilePermissions.fromString(dir ? "rwxrwxrwx" : "rw-r--r--"));
            }
        } catch (UnsupportedOperationException ignored) {
            // non-POSIX
        }
    }

    private static List<String> timeoutWrap(List<String> cmd, double seconds) {
        List<String> out = new ArrayList<>(List.of("timeout", "-k", "1", String.format(java.util.Locale.ROOT, "%.3f", seconds)));
        out.addAll(cmd);
        return out;
    }

    private static String trim(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "\n...[truncated]";
    }

    private static long ms(long t0) {
        return (System.nanoTime() - t0) / 1_000_000;
    }

    private static void deleteQuietly(Path dir) {
        try (Stream<Path> s = Files.walk(dir)) {
            s.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // best effort; the work root is under java.io.tmpdir
                }
            });
        } catch (IOException ignored) {
            // best effort
        }
    }
}
