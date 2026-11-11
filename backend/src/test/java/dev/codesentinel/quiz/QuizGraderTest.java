package dev.codesentinel.quiz;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.codesentinel.quiz.strategy.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class QuizGraderTest {
    static final ObjectMapper M = new ObjectMapper();
    static final QuestionGraderRegistry REGISTRY = new QuestionGraderRegistry(List.of(
            new McqGradingStrategy(),
            new MultiSelectGradingStrategy(),
            new TrueFalseGradingStrategy(),
            new ShortAnswerGradingStrategy(),
            new OutputGradingStrategy(),
            new FillBlankGradingStrategy(),
            new ManualReviewGradingStrategy()
    ));
    static final QuizGrader GRADER = new QuizGrader(REGISTRY);

    static JsonNode j(String s) {
        try {
            return M.readTree(s);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static BigDecimal g(String type, double marks, String answer, String settings, String response) {
        return GRADER.grade(type, BigDecimal.valueOf(marks), j(answer), j(settings), j(response));
    }

    @Test
    void mcqFullWrongBlankAndNegative() {
        assertEquals(new BigDecimal("5.00"), g("MCQ", 5, "{\"correct\":[2]}", "{}", "{\"choice\":2}"));
        assertEquals(new BigDecimal("0.00"), g("MCQ", 5, "{\"correct\":[2]}", "{}", "{\"choice\":1}"));
        assertEquals(new BigDecimal("-1.25"), g("MCQ", 5, "{\"correct\":[2]}", "{\"negative\":0.25}", "{\"choice\":1}"));
        assertEquals(new BigDecimal("0.00"), g("MCQ", 5, "{\"correct\":[2]}", "{\"negative\":0.25}", "{}"), "a blank answer is never penalised");
    }

    @Test
    void multiAllOrNothingAndPartial() {
        assertEquals(new BigDecimal("4.00"), g("MULTI", 4, "{\"correct\":[0,2]}", "{}", "{\"choices\":[2,0]}"));
        assertEquals(new BigDecimal("0.00"), g("MULTI", 4, "{\"correct\":[0,2]}", "{}", "{\"choices\":[0]}"));
        assertEquals(new BigDecimal("2.00"), g("MULTI", 4, "{\"correct\":[0,2]}", "{\"partial\":true}", "{\"choices\":[0]}"));
        assertEquals(new BigDecimal("0.00"), g("MULTI", 4, "{\"correct\":[0,2]}", "{\"partial\":true}", "{\"choices\":[0,1]}"), "a wrong pick cancels a right one");
        assertEquals(new BigDecimal("0.00"), g("MULTI", 4, "{\"correct\":[0,2]}", "{\"partial\":true}", "{\"choices\":[1,3]}"), "never below zero");
    }

    @Test
    void trueFalse() {
        assertEquals(new BigDecimal("2.00"), g("TRUE_FALSE", 2, "{\"value\":false}", "{}", "{\"value\":false}"));
        assertEquals(new BigDecimal("0.00"), g("TRUE_FALSE", 2, "{\"value\":false}", "{}", "{\"value\":true}"));
    }

    @Test
    void shortAnswersIgnoreCaseAndSpacingUnlessAsked() {
        assertEquals(new BigDecimal("3.00"), g("SHORT", 3, "{\"accepted\":[\"Binary Search\",\"bsearch\"]}", "{}", "{\"text\":\"  binary   search \"}"));
        assertEquals(new BigDecimal("0.00"), g("SHORT", 3, "{\"accepted\":[\"Binary Search\"]}", "{\"caseSensitive\":true}", "{\"text\":\"binary search\"}"));
        assertNull(g("SHORT", 3, "{\"accepted\":[]}", "{}", "{\"text\":\"anything\"}"), "no key: marked by hand");
        assertNull(g("LONG", 10, "{}", "{}", "{\"text\":\"essay\"}"));
        assertNull(g("FILE", 10, "{}", "{}", "{}"));
        assertNull(g("CODE", 10, "{}", "{}", "{}"));
    }

    @Test
    void outputPredictionIsCaseSensitiveButForgivesTrailingWhitespace() {
        assertEquals(new BigDecimal("5.00"), g("OUTPUT", 5, "{\"accepted\":[\"1\\n2\\n3\"]}", "{}", "{\"text\":\"1  \\r\\n2\\n3\\n\\n\"}"));
        assertEquals(new BigDecimal("0.00"), g("OUTPUT", 5, "{\"accepted\":[\"True\"]}", "{}", "{\"text\":\"true\"}"));
        assertEquals(new BigDecimal("0.00"), g("OUTPUT", 5, "{\"accepted\":[\"1 2\"]}", "{}", "{\"text\":\"1  2\"}"), "inner spacing matters for output");
    }

    @Test
    void fillInTheBlanksGivesPartialCreditByDefault() {
        String ans = "{\"blanks\":[[\"O(n log n)\",\"n log n\"],[\"O(n^2)\"]]}";
        assertEquals(new BigDecimal("6.00"), g("FILL_BLANK", 6, ans, "{}", "{\"blanks\":[\"n log n\",\"o(n^2)\"]}"));
        assertEquals(new BigDecimal("3.00"), g("FILL_BLANK", 6, ans, "{}", "{\"blanks\":[\"n log n\",\"\"]}"));
        assertEquals(new BigDecimal("0.00"), g("FILL_BLANK", 6, ans, "{\"partial\":false}", "{\"blanks\":[\"n log n\",\"\"]}"));
    }
}
