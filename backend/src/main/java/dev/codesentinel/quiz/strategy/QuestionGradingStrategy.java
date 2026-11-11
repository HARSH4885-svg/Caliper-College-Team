package dev.codesentinel.quiz.strategy;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Set;

/**
 * Strategy interface defining polymorphic grading behavior for a specific question type.
 * Demonstrates Abstraction, Polymorphism, and Open/Closed Principle (OCP).
 */
public interface QuestionGradingStrategy {

    /** The primary question type identifier (e.g. "MCQ", "MULTI", "TRUE_FALSE"). */
    String type();

    /**
     * All question type identifiers supported by this strategy.
     * Allows a single strategy bean (such as ManualReviewGradingStrategy) to handle multiple types.
     */
    default Set<String> supportedTypes() {
        String t = type();
        return t != null ? Set.of(t.toUpperCase(Locale.ROOT)) : Set.of();
    }

    /** Whether questions of this type can be automatically graded given the answer key. */
    boolean isAutoGradable(JsonNode answer);

    /**
     * Grades a response against the answer key and settings.
     *
     * @return awarded marks scaled to 2 decimal places, or null if manual review is required.
     */
    BigDecimal grade(BigDecimal marks, JsonNode answer, JsonNode settings, JsonNode response);
}
