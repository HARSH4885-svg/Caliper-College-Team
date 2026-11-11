package dev.codesentinel.quiz.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.Set;

/**
 * Strategy for grading Multi-Select Questions (MULTI) with all-or-nothing or partial scoring.
 */
@Component
public class MultiSelectGradingStrategy extends AbstractQuestionGradingStrategy {

    public MultiSelectGradingStrategy() {
        super("MULTI");
    }

    @Override
    public boolean isAutoGradable(JsonNode answer) {
        return answer != null && answer.path("correct").isArray() && !answer.path("correct").isEmpty();
    }

    @Override
    public BigDecimal grade(BigDecimal marks, JsonNode answer, JsonNode settings, JsonNode response) {
        if (!isAutoGradable(answer)) return null;
        Set<Integer> key = new HashSet<>();
        for (JsonNode n : answer.path("correct")) key.add(n.asInt());

        Set<Integer> pick = new HashSet<>();
        if (response != null && response.path("choices").isArray()) {
            for (JsonNode n : response.path("choices")) pick.add(n.asInt());
        }

        boolean partial = settings != null && settings.path("partial").asBoolean(false);
        if (!partial) {
            return scale(pick.equals(key) ? marks : BigDecimal.ZERO);
        }

        int right = 0;
        int wrong = 0;
        for (int p : pick) {
            if (key.contains(p)) right++;
            else wrong++;
        }
        int net = Math.max(0, right - wrong);
        if (net == 0 || key.isEmpty()) return scale(BigDecimal.ZERO);

        return marks.multiply(BigDecimal.valueOf(net))
                .divide(BigDecimal.valueOf(key.size()), 2, RoundingMode.HALF_UP);
    }
}
