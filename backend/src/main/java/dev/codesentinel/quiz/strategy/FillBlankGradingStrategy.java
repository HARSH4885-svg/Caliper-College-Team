package dev.codesentinel.quiz.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.Set;

/**
 * Strategy for grading Fill-in-the-Blank questions (BLANK and FILL_BLANK).
 */
@Component
public class FillBlankGradingStrategy extends AbstractQuestionGradingStrategy {

    private static final Set<String> TYPES = Set.of("BLANK", "FILL_BLANK");

    public FillBlankGradingStrategy() {
        super("BLANK");
    }

    @Override
    public Set<String> supportedTypes() {
        return TYPES;
    }

    @Override
    public boolean isAutoGradable(JsonNode answer) {
        return answer != null && answer.path("blanks").isArray() && !answer.path("blanks").isEmpty();
    }

    @Override
    public BigDecimal grade(BigDecimal marks, JsonNode answer, JsonNode settings, JsonNode response) {
        if (!isAutoGradable(answer)) return null;
        JsonNode b = answer.path("blanks");
        int total = b.size();
        if (total == 0) return scale(BigDecimal.ZERO);

        boolean caseSensitive = settings != null && settings.path("caseSensitive").asBoolean(false);
        int correct = 0;
        for (int i = 0; i < total; i++) {
            String text = response == null ? "" : response.path("blanks").path(i).asText("");
            String given = normalize(text, caseSensitive, false);

            Set<String> accepted = new HashSet<>();
            for (JsonNode n : b.get(i)) {
                accepted.add(normalize(n.asText(""), caseSensitive, false));
            }
            if (accepted.contains(given)) correct++;
        }

        boolean partial = settings == null || settings.path("partial").asBoolean(true);
        if (!partial) {
            return scale(correct == total ? marks : BigDecimal.ZERO);
        }

        if (correct == 0) return scale(BigDecimal.ZERO);
        return marks.multiply(BigDecimal.valueOf(correct))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }
}
