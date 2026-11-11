package dev.codesentinel.judge.verdict;

import dev.codesentinel.judge.sandbox.RunResult;
import dev.codesentinel.judge.testcase.OutputComparator;

/**
 * Verdict rules (section 27) as a pure function so they can be unit tested without Docker.
 *
 * <ul>
 *   <li>TLE: the in-container {@code timeout} fired (exit 124/137 without an OOM kill) or the host wall-clock guard fired.</li>
 *   <li>MLE: Docker reports the container was OOM-killed (proven), or the runtime reported an out-of-memory error
 *       itself (JVM OutOfMemoryError, C++ bad_alloc - reported as a heuristic in the detail text).</li>
 *   <li>RE: non-zero exit/crash without a limit classification, or output limit exceeded.</li>
 *   <li>WA: exit 0 but output differs under the documented comparison policy; AC otherwise.</li>
 * </ul>
 */
public final class VerdictClassifier {
    private VerdictClassifier() {
    }

    public record Classified(Verdict verdict, String detail) {
    }

    public static Classified classify(RunResult r, String expected) {
        if (r.oomKilled()) return new Classified(Verdict.MLE, "Memory limit exceeded (container was OOM-killed).");
        if (r.timedOut()) return new Classified(Verdict.TLE, "Time limit exceeded (wall-clock guard).");
        if (r.outputLimitExceeded()) return new Classified(Verdict.RE, "Output limit exceeded; the program was stopped.");
        if (r.exitCode() == 124 || r.exitCode() == 137) {
            return new Classified(Verdict.TLE, "Time limit exceeded.");
        }
        if (r.exitCode() != 0) {
            String err = r.stderr();
            if (err.contains("java.lang.OutOfMemoryError") || err.contains("std::bad_alloc") || err.contains("MemoryError")
                    || err.contains("JavaScript heap out of memory")) {
                return new Classified(Verdict.MLE, "Memory limit exceeded (reported by the runtime; heuristic classification).");
            }
            return new Classified(Verdict.RE, "Runtime error (exit code " + r.exitCode() + (r.exitCode() > 128 ? ", signal " + (r.exitCode() - 128) : "") + ").");
        }
        return OutputComparator.matches(expected, r.stdout())
                ? new Classified(Verdict.AC, "Accepted.")
                : new Classified(Verdict.WA, "Wrong answer.");
    }

    /** Aggregate: IE beats everything; CE is decided before tests; otherwise the first non-AC in test order. */
    public static Verdict aggregate(java.util.List<TestCaseResult> results) {
        if (results.isEmpty()) return Verdict.IE;
        for (TestCaseResult t : results) if (t.verdict() == Verdict.IE) return Verdict.IE;
        for (TestCaseResult t : results) if (t.verdict() != Verdict.AC) return t.verdict();
        return Verdict.AC;
    }
}
