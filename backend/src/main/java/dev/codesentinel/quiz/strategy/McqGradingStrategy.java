package dev.codesentinel.quiz.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Strategy for grading single-choice Multiple Choice Questions (MCQ).
 */
@Component
public class McqGradingStrategy extends AbstractQuestionGradingStrategy {

    public McqGradingStrategy() {
        super("MCQ");
    }

    @Override
    public boolean isAutoGradable(JsonNode answer) {
        return answer != null && answer.path("correct").isArray() && !answer.path("correct").isEmpty();
    }

    @Override
    public BigDecimal grade(BigDecimal marks, JsonNode answer, JsonNode settings, JsonNode response) {
        if (!isAutoGradable(answer)) return null;
        JsonNode choice = response == null ? null : response.path("choice");
        if (choice == null || choice.isMissingNode() || choice.isNull()) return scale(BigDecimal.ZERO);

        int pick = choice.asInt();
        int key = answer.path("correct").get(0).asInt();
        if (pick == key) return scale(marks);

        BigDecimal neg = settings == null ? null : BigDecimal.valueOf(settings.path("negative").asDouble(0.0));
        if (neg != null && neg.signum() > 0) {
            return scale(marks.multiply(neg).negate());
        }
        return scale(BigDecimal.ZERO);
    }
}
