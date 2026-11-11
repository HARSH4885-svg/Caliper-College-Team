package dev.codesentinel.assignments;

import java.time.Instant;
import java.util.List;

public record AssignmentDto(
        long id,
        long courseId,
        String title,
        String description,
        String language,
        Instant deadline,
        int timeLimitMs,
        int memoryLimitMb,
        boolean published,
        boolean archived,
        List<FileDto> files,
        List<TestCaseDto> testCases,
        int hiddenTestCount,
        String kind,
        String category,
        String difficulty,
        java.math.BigDecimal maxMarks,
        java.math.BigDecimal passingMarks,
        Instant availableFrom,
        int maxAttempts,
        String latePolicy,
        int latePenaltyPct,
        Instant lateUntil,
        Integer quizMinutes,
        boolean shuffleQuestions,
        boolean shuffleOptions,
        String releaseMode,
        boolean showAnswers,
        int questionCount
) {
}
