package dev.codesentinel.quiz.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

/**
 * Spring-managed registry managing question grading strategies.
 * Implements the Registry pattern and Strategy dispatch, enabling Open/Closed Principle compliance.
 * Validates duplicate identifiers during initialization and provides an immutable strategy lookup map.
 */
@Component
public class QuestionGraderRegistry {

    private final Map<String, QuestionGradingStrategy> strategies;

    public QuestionGraderRegistry(List<QuestionGradingStrategy> strategyList) {
        Map<String, QuestionGradingStrategy> map = new LinkedHashMap<>();
        if (strategyList != null) {
            for (QuestionGradingStrategy strategy : strategyList) {
                for (String t : strategy.supportedTypes()) {
                    String norm = t.trim().toUpperCase(Locale.ROOT);
                    if (map.containsKey(norm)) {
                        throw new IllegalStateException("Duplicate question grading strategy registered for type: " + norm);
                    }
                    map.put(norm, strategy);
                }
            }
        }
        this.strategies = Collections.unmodifiableMap(map);
    }

    public Optional<QuestionGradingStrategy> forType(String type) {
        if (type == null) return Optional.empty();
        return Optional.ofNullable(strategies.get(type.trim().toUpperCase(Locale.ROOT)));
    }

    public boolean isAutoGradable(String type, JsonNode answer) {
        return forType(type).map(s -> s.isAutoGradable(answer)).orElse(false);
    }

    public BigDecimal grade(String type, BigDecimal marks, JsonNode answer, JsonNode settings, JsonNode response) {
        return forType(type).map(s -> s.grade(marks, answer, settings, response)).orElse(null);
    }

    public Set<String> supportedTypes() {
        return strategies.keySet();
    }

    public boolean isSupported(String type) {
        if (type == null) return false;
        return strategies.containsKey(type.trim().toUpperCase(Locale.ROOT));
    }
}
