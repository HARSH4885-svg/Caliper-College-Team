package dev.codesentinel.quiz.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

/**
 * Strategy for grading Program Output Prediction questions (OUTPUT).
 */
@Component
public class OutputGradingStrategy extends AbstractQuestionGradingStrategy {

    public OutputGradingStrategy() {
        super("OUTPUT");
    }

    @Override
    public boolean isAutoGradable(JsonNode answer) {
        return answer != null && answer.path("accepted").isArray() && !answer.path("accepted").isEmpty();
    }

    @Override
    public BigDecimal grade(BigDecimal marks, JsonNode answer, JsonNode settings, JsonNode response) {
        if (!isAutoGradable(answer)) return null;
        String text = response == null ? "" : response.path("text").asText("");
        String given = normalize(text, true, true);

        Set<String> accepted = new HashSet<>();
        for (JsonNode n : answer.path("accepted")) {
            accepted.add(normalize(n.asText(""), true, true));
        }

        return scale(accepted.contains(given) ? marks : BigDecimal.ZERO);
    }
}
