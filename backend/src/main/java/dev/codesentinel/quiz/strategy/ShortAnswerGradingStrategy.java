package dev.codesentinel.quiz.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

/**
 * Strategy for grading Short Answer questions (SHORT).
 */
@Component
public class ShortAnswerGradingStrategy extends AbstractQuestionGradingStrategy {

    public ShortAnswerGradingStrategy() {
        super("SHORT");
    }

    @Override
    public boolean isAutoGradable(JsonNode answer) {
        return answer != null && answer.path("accepted").isArray() && !answer.path("accepted").isEmpty();
    }

    @Override
    public BigDecimal grade(BigDecimal marks, JsonNode answer, JsonNode settings, JsonNode response) {
        if (!isAutoGradable(answer)) return null;
        boolean caseSensitive = settings != null && settings.path("caseSensitive").asBoolean(false);
        String text = response == null ? "" : response.path("text").asText("");
        String given = normalize(text, caseSensitive, false);

        Set<String> accepted = new HashSet<>();
        for (JsonNode n : answer.path("accepted")) {
            accepted.add(normalize(n.asText(""), caseSensitive, false));
        }

        return scale(accepted.contains(given) ? marks : BigDecimal.ZERO);
    }
}
