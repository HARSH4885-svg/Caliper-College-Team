package dev.codesentinel.quiz;

import com.fasterxml.jackson.databind.JsonNode;
import dev.codesentinel.quiz.strategy.QuestionGraderRegistry;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Set;

/**
 * Service providing automatic marking for question types.
 * Delegates to the polymorphic {@link dev.codesentinel.quiz.strategy.QuestionGradingStrategy} hierarchy managed by {@link QuestionGraderRegistry},
 * adhering to the Open/Closed Principle (OCP) and Polymorphism.
 */
@Service
public class QuizGrader {

    private final QuestionGraderRegistry registry;

    public QuizGrader(QuestionGraderRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "QuestionGraderRegistry must not be null");
    }

    public boolean autoGradable(String type, JsonNode answer) {
        return registry.isAutoGradable(type, answer);
    }

    public BigDecimal grade(String type, BigDecimal marks, JsonNode answer, JsonNode settings, JsonNode response) {
        return registry.grade(type, marks, answer, settings, response);
    }

    public Set<String> supportedTypes() {
        return registry.supportedTypes();
    }

    public boolean isSupported(String type) {
        return registry.isSupported(type);
    }
}
