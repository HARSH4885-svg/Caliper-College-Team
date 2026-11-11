package dev.codesentinel.assignments;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Assessment settings shared by create and update; every field is optional on update.
 */
public record Options(
        @Pattern(regexp = "ASSIGNMENT|QUIZ|LAB|EXAM|PRACTICE") String category,
        @Pattern(regexp = "EASY|MEDIUM|HARD") String difficulty,
        @DecimalMin("0.5") @DecimalMax("10000") BigDecimal maxMarks,
        @DecimalMin("0") BigDecimal passingMarks,
        Instant availableFrom,
        Boolean clearAvailableFrom,
        @Min(1) @Max(50) Integer maxAttempts,
        @Pattern(regexp = "BLOCK|ALLOW|PENALTY") String latePolicy,
        @Min(0) @Max(100) Integer latePenaltyPct,
        Instant lateUntil,
        Boolean clearLateUntil,
        @Min(1) @Max(600) Integer quizMinutes,
        Boolean clearQuizMinutes,
        Boolean shuffleQuestions,
        Boolean shuffleOptions,
        @Pattern(regexp = "IMMEDIATE|AFTER_DUE|MANUAL") String releaseMode,
        Boolean showAnswers
) {
}
