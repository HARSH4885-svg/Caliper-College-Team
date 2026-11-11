package dev.codesentinel.quiz.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Strategy for question types requiring manual instructor grading (e.g. LONG essays, FILE submissions, CODE solutions).
 * Supports LONG, FILE, and CODE types in a single Spring component, eliminating redundant class definitions.
 */
@Component
public class ManualReviewGradingStrategy extends AbstractQuestionGradingStrategy {

    private static final Set<String> MANUAL_TYPES = Set.of("LONG", "FILE", "CODE");

    public ManualReviewGradingStrategy() {
        super("MANUAL");
    }

    public ManualReviewGradingStrategy(String type) {
        super(type);
    }

    @Override
    public Set<String> supportedTypes() {
        if ("MANUAL".equalsIgnoreCase(this.type)) {
            return MANUAL_TYPES;
        }
        return super.supportedTypes();
    }

    @Override
    public boolean isAutoGradable(JsonNode answer) {
        return false;
    }

    @Override
    public BigDecimal grade(BigDecimal marks, JsonNode answer, JsonNode settings, JsonNode response) {
        return null; // requires manual instructor evaluation
    }
}
