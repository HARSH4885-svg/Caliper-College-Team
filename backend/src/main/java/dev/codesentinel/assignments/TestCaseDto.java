package dev.codesentinel.assignments;

/**
 * input/expectedOutput are null for hidden tests when the viewer is a student - but students never receive hidden rows at all.
 */
public record TestCaseDto(Long id, String name, boolean sample, String input, String expectedOutput) {
}
