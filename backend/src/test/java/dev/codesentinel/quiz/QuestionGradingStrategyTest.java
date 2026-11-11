package dev.codesentinel.quiz;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.codesentinel.quiz.strategy.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class QuestionGradingStrategyTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private QuestionGraderRegistry registry;
    private QuizGrader quizGrader;

    @BeforeEach
    void setUp() {
        registry = new QuestionGraderRegistry(List.of(
                new McqGradingStrategy(),
                new MultiSelectGradingStrategy(),
                new TrueFalseGradingStrategy(),
                new ShortAnswerGradingStrategy(),
                new OutputGradingStrategy(),
                new FillBlankGradingStrategy(),
                new ManualReviewGradingStrategy()
        ));
        quizGrader = new QuizGrader(registry);
    }

    private JsonNode json(String s) {
        try {
            return MAPPER.readTree(s);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("Registry discovers and returns correct strategy per question type")
    void registryResolvesStrategies() {
        assertTrue(registry.forType("MCQ").isPresent());
        assertInstanceOf(McqGradingStrategy.class, registry.forType("MCQ").get());

        assertTrue(registry.forType("multi").isPresent());
        assertInstanceOf(MultiSelectGradingStrategy.class, registry.forType("MULTI").get());

        assertTrue(registry.forType("TRUE_FALSE").isPresent());
        assertInstanceOf(TrueFalseGradingStrategy.class, registry.forType("TRUE_FALSE").get());

        assertTrue(registry.forType("short").isPresent());
        assertInstanceOf(ShortAnswerGradingStrategy.class, registry.forType("SHORT").get());

        assertTrue(registry.forType("OUTPUT").isPresent());
        assertInstanceOf(OutputGradingStrategy.class, registry.forType("OUTPUT").get());

        assertTrue(registry.forType("BLANK").isPresent());
        assertInstanceOf(FillBlankGradingStrategy.class, registry.forType("BLANK").get());

        assertTrue(registry.forType("LONG").isPresent());
        assertInstanceOf(ManualReviewGradingStrategy.class, registry.forType("LONG").get());

        assertTrue(registry.forType("FILE").isPresent());
        assertInstanceOf(ManualReviewGradingStrategy.class, registry.forType("FILE").get());

        assertTrue(registry.forType("CODE").isPresent());
        assertInstanceOf(ManualReviewGradingStrategy.class, registry.forType("CODE").get());

        assertFalse(registry.forType("UNKNOWN_TYPE").isPresent());
        assertFalse(registry.isSupported("UNKNOWN_TYPE"));
    }

    @Test
    @DisplayName("Registry detects duplicate strategy registrations and throws IllegalStateException")
    void registryDetectsDuplicates() {
        assertThrows(IllegalStateException.class, () -> new QuestionGraderRegistry(List.of(
                new McqGradingStrategy(),
                new McqGradingStrategy()
        )));
        assertThrows(IllegalStateException.class, () -> new QuestionGraderRegistry(List.of(
                new ManualReviewGradingStrategy(),
                new AbstractQuestionGradingStrategy("CODE") {
                    @Override
                    public boolean isAutoGradable(JsonNode answer) {
                        return false;
                    }
                    @Override
                    public BigDecimal grade(BigDecimal marks, JsonNode answer, JsonNode settings, JsonNode response) {
                        return null;
                    }
                }
        )));
    }

    @Test
    @DisplayName("QuizGrader service delegates correctly to injected strategy registry")
    void quizGraderServiceDelegation() {
        assertTrue(quizGrader.isSupported("MCQ"));
        assertTrue(quizGrader.isSupported("LONG"));
        assertFalse(quizGrader.isSupported("NON_EXISTENT"));

        BigDecimal score = quizGrader.grade("MCQ", BigDecimal.valueOf(5), json("{\"correct\":[1]}"), json("{}"), json("{\"choice\":1}"));
        assertEquals(new BigDecimal("5.00"), score);
    }

    @Test
    @DisplayName("McqGradingStrategy correctly scores correct, incorrect, and negative penalty cases")
    void mcqStrategyScoring() {
        QuestionGradingStrategy strategy = new McqGradingStrategy();
        assertEquals("MCQ", strategy.type());
        assertTrue(strategy.isAutoGradable(json("{\"correct\":[1]}")));

        BigDecimal score = strategy.grade(BigDecimal.valueOf(10.0), json("{\"correct\":[1]}"), json("{\"negative\":0.5}"), json("{\"choice\":1}"));
        assertEquals(new BigDecimal("10.00"), score);

        BigDecimal penalty = strategy.grade(BigDecimal.valueOf(10.0), json("{\"correct\":[1]}"), json("{\"negative\":0.5}"), json("{\"choice\":2}"));
        assertEquals(new BigDecimal("-5.00"), penalty);
    }

    @Test
    @DisplayName("ShortAnswerGradingStrategy respects case-sensitivity setting")
    void shortAnswerCaseSensitivity() {
        QuestionGradingStrategy strategy = new ShortAnswerGradingStrategy();
        JsonNode answer = json("{\"accepted\":[\"MergeSort\"]}");

        BigDecimal caseInsensitive = strategy.grade(BigDecimal.valueOf(5.0), answer, json("{\"caseSensitive\":false}"), json("{\"text\":\"  mergesort \"}"));
        assertEquals(new BigDecimal("5.00"), caseInsensitive);

        BigDecimal caseSensitiveFail = strategy.grade(BigDecimal.valueOf(5.0), answer, json("{\"caseSensitive\":true}"), json("{\"text\":\"mergesort\"}"));
        assertEquals(new BigDecimal("0.00"), caseSensitiveFail);
    }
}
