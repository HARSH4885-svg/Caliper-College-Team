package dev.codesentinel.judge.verdict;

import java.util.List;

public record JudgeResult(Verdict verdict, String message, String compileLog, int passed, int total, long totalMillis,
                          List<TestCaseResult> results) {
    public static JudgeResult internalError(String message) {
        return new JudgeResult(Verdict.IE, message, "", 0, 0, 0, List.of());
    }
}
