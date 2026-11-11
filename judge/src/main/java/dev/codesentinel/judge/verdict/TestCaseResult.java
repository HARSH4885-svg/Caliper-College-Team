package dev.codesentinel.judge.verdict;

/**
 * Per-testcase outcome. {@code stdoutExcerpt}/{@code stderrExcerpt} are bounded; the portal must only show them to a
 * student for sample tests (FR-JUDGE-11).
 */
public record TestCaseResult(String name, boolean sample, Verdict verdict, long millis, String detail,
                             String stdoutExcerpt, String stderrExcerpt) {
}
