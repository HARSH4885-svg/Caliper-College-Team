package dev.codesentinel.judge.testcase;

/**
 * @param sample true for sample tests whose input/expected output students may see; false for hidden tests
 */
public record TestCase(String name, String input, String expectedOutput, boolean sample) {
}
