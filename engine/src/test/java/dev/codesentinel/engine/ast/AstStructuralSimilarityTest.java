package dev.codesentinel.engine.ast;

import dev.codesentinel.engine.api.SourceFileInput;
import dev.codesentinel.engine.api.SubmissionInput;
import dev.codesentinel.engine.lexer.Language;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AstStructuralSimilarityTest {

    private AstStructuralAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new AstStructuralAnalyzer();
    }

    @Test
    @DisplayName("Identical code produces very high structural similarity (> 0.95)")
    void testIdenticalCodeHighSimilarity() {
        String code = """
                public class Calculator {
                    public int fibonacci(int n) {
                        if (n <= 1) {
                            return n;
                        }
                        int a = 0;
                        int b = 1;
                        for (int i = 2; i <= n; i++) {
                            int sum = a + b;
                            a = b;
                            b = sum;
                        }
                        return b;
                    }
                }
                """;

        SubmissionInput subA = new SubmissionInput("subA", List.of(new SourceFileInput("Calculator.java", code)));
        SubmissionInput subB = new SubmissionInput("subB", List.of(new SourceFileInput("Calculator.java", code)));

        AstStructuralResult result = analyzer.compare(subA, subB, Language.JAVA);

        assertNotNull(result);
        assertTrue(result.structuralScore() >= 0.95, "Identical code score should be >= 0.95, got: " + result.structuralScore());
        assertTrue(result.sharedSubtrees() > 10, "Should share multiple structural subtrees");
        assertFalse(result.matchedFragments().isEmpty(), "Should report matched fragments");
    }

    @Test
    @DisplayName("Renamed variables and modified identifiers retain high AST structural similarity (> 0.90)")
    void testAlphaRenamingRetainsStructuralScore() {
        String codeA = """
                public class Sorter {
                    public static void bubbleSort(int[] array) {
                        int n = array.length;
                        for (int i = 0; i < n - 1; i++) {
                            for (int j = 0; j < n - i - 1; j++) {
                                if (array[j] > array[j + 1]) {
                                    int temp = array[j];
                                    array[j] = array[j + 1];
                                    array[j + 1] = temp;
                                }
                            }
                        }
                    }
                }
                """;

        // Renamed method, variables (array -> list, n -> len, i -> p, j -> q, temp -> aux), altered whitespace
        String codeB = """
                public class OrderUtil {
                    public static void sortInts(int[] list) {
                        int len = list.length;
                        for (int p = 0; p < len - 1; p++) {
                            for (int q = 0; q < len - p - 1; q++) {
                                if (list[q] > list[q + 1]) {
                                    int aux = list[q];
                                    list[q] = list[q + 1];
                                    list[q + 1] = aux;
                                }
                            }
                        }
                    }
                }
                """;

        SubmissionInput subA = new SubmissionInput("subA", List.of(new SourceFileInput("Sorter.java", codeA)));
        SubmissionInput subB = new SubmissionInput("subB", List.of(new SourceFileInput("OrderUtil.java", codeB)));

        AstStructuralResult result = analyzer.compare(subA, subB, Language.JAVA);

        assertNotNull(result);
        assertTrue(result.structuralScore() >= 0.90, "Alpha-renamed code AST structural score should be >= 0.90, got: " + result.structuralScore());
        assertTrue(result.sharedSubtrees() >= 15);
    }

    @Test
    @DisplayName("Completely independent algorithms produce low structural similarity (< 0.30)")
    void testDifferentAlgorithmsLowStructuralScore() {
        String codeA = """
                public class PrimeChecker {
                    public boolean isPrime(int n) {
                        if (n <= 1) return false;
                        for (int i = 2; i * i <= n; i++) {
                            if (n % i == 0) return false;
                        }
                        return true;
                    }
                }
                """;

        String codeB = """
                import java.util.Stack;
                public class Parentheses {
                    public boolean isValid(String s) {
                        Stack<Character> stack = new Stack<>();
                        for (char c : s.toCharArray()) {
                            if (c == '(') stack.push(')');
                            else if (stack.isEmpty() || stack.pop() != c) return false;
                        }
                        return stack.isEmpty();
                    }
                }
                """;

        SubmissionInput subA = new SubmissionInput("subA", List.of(new SourceFileInput("PrimeChecker.java", codeA)));
        SubmissionInput subB = new SubmissionInput("subB", List.of(new SourceFileInput("Parentheses.java", codeB)));

        AstStructuralResult result = analyzer.compare(subA, subB, Language.JAVA);

        assertNotNull(result);
        assertTrue(result.structuralScore() < 0.35, "Independent code score should be low, got: " + result.structuralScore());
    }

    @Test
    @DisplayName("Supports Python, C, C++, and JavaScript AST parsing without error")
    void testMultiLanguageSupport() {
        // Python
        String pyA = "def add(x, y):\n    return x + y\n";
        String pyB = "def sum_vals(a, b):\n    return a + b\n";
        AstStructuralResult pyRes = analyzer.compare(
                new SubmissionInput("A", List.of(new SourceFileInput("a.py", pyA))),
                new SubmissionInput("B", List.of(new SourceFileInput("b.py", pyB))),
                Language.PYTHON
        );
        assertTrue(pyRes.structuralScore() >= 0.85);

        // C
        String cA = "int max(int a, int b) { return a > b ? a : b; }";
        String cB = "int maximum(int x, int y) { return x > y ? x : y; }";
        AstStructuralResult cRes = analyzer.compare(
                new SubmissionInput("A", List.of(new SourceFileInput("a.c", cA))),
                new SubmissionInput("B", List.of(new SourceFileInput("b.c", cB))),
                Language.C
        );
        assertTrue(cRes.structuralScore() >= 0.85);

        // C++
        String cppA = "int square(int val) { int res = val * val; return res; }";
        String cppB = "int sq(int n) { int ans = n * n; return ans; }";
        AstStructuralResult cppRes = analyzer.compare(
                new SubmissionInput("A", List.of(new SourceFileInput("a.cpp", cppA))),
                new SubmissionInput("B", List.of(new SourceFileInput("b.cpp", cppB))),
                Language.CPP
        );
        assertTrue(cppRes.structuralScore() >= 0.85);

        // JavaScript
        String jsA = "function double(n) { const out = n * 2; return out; }";
        String jsB = "function mult(x) { const val = x * 2; return val; }";
        AstStructuralResult jsRes = analyzer.compare(
                new SubmissionInput("A", List.of(new SourceFileInput("a.js", jsA))),
                new SubmissionInput("B", List.of(new SourceFileInput("b.js", jsB))),
                Language.JAVASCRIPT
        );
        assertTrue(jsRes.structuralScore() >= 0.85);
    }
}
