package dev.codesentinel.quiz.strategy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Set;

/**
 * Abstract base strategy providing shared logic for string normalization,
 * output matching, and score scaling.
 * Demonstrates Inheritance and Behavioral Reuse.
 */
public abstract class AbstractQuestionGradingStrategy implements QuestionGradingStrategy {

    protected final String type;

    protected AbstractQuestionGradingStrategy(String type) {
        this.type = type != null ? type.toUpperCase(Locale.ROOT) : null;
    }

    @Override
    public String type() {
        return type;
    }

    @Override
    public Set<String> supportedTypes() {
        return type != null ? Set.of(type) : Set.of();
    }

    /**
     * Helper to scale a numeric score to 2 decimal places with HALF_UP rounding.
     */
    protected BigDecimal scale(BigDecimal val) {
        if (val == null) return null;
        return val.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Normalizes a text string for comparison.
     * Short answers: trimmed, inner whitespace collapsed.
     * Program output: trailing spaces per line and trailing blank lines ignored.
     */
    public static String normalize(String s, boolean caseSensitive, boolean output) {
        if (s == null) return "";
        if (output) {
            String[] lines = s.split("\r?\n", -1);
            StringBuilder b = new StringBuilder();
            int lastNonBlank = -1;
            for (int i = 0; i < lines.length; i++) {
                String trimmed = lines[i].replaceAll("\\s+$", "");
                lines[i] = trimmed;
                if (!trimmed.isEmpty()) lastNonBlank = i;
            }
            for (int i = 0; i <= lastNonBlank; i++) {
                if (i > 0) b.append('\n');
                b.append(lines[i]);
            }
            return b.toString();
        }
        String t = s.trim().replaceAll("\\s+", " ");
        return caseSensitive ? t : t.toLowerCase(Locale.ROOT);
    }
}
