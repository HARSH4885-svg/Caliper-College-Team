package dev.codesentinel.quiz.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Strategy for grading True/False questions (TRUE_FALSE).
 */
@Component
public class TrueFalseGradingStrategy extends AbstractQuestionGradingStrategy {

    public TrueFalseGradingStrategy() {
        super("TRUE_FALSE");
    }

    @Override
    public boolean isAutoGradable(JsonNode answer) {
        return answer != null && answer.path("value").isBoolean();
    }

    @Override
    public BigDecimal grade(BigDecimal marks, JsonNode answer, JsonNode settings, JsonNode response) {
        if (!isAutoGradable(answer)) return null;
        JsonNode val = response == null ? null : response.path("value");
        if (val == null || !val.isBoolean()) return scale(BigDecimal.ZERO);

        boolean pick = val.asBoolean();
        boolean key = answer.path("value").asBoolean();
        return scale(pick == key ? marks : BigDecimal.ZERO);
    }
}
