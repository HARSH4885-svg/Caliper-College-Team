package dev.codesentinel.assignments;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TestCaseInput(
        @NotBlank @Size(max = 80) String name,
        @Size(max = 100000) String input,
        @NotNull @Size(max = 100000) String expectedOutput,
        boolean sample
) {
}
