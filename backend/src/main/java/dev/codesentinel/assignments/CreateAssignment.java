package dev.codesentinel.assignments;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.Instant;

public record CreateAssignment(
        @NotBlank @Size(max = 150) String title,
        @NotBlank @Size(max = 20000) String description,
        @Pattern(regexp = "c|cpp|java|python|javascript|none") String language,
        @NotNull Instant deadline,
        @Min(100) @Max(20000) Integer timeLimitMs,
        @Min(16) @Max(1024) Integer memoryLimitMb,
        @Pattern(regexp = "CODING|QUIZ") String kind,
        @Valid Options options
) {
}
