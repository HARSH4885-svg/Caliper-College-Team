package dev.codesentinel.assignments;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record PatchAssignment(
        @Size(max = 150) String title,
        @Size(max = 20000) String description,
        Instant deadline,
        @Min(100) @Max(20000) Integer timeLimitMs,
        @Min(16) @Max(1024) Integer memoryLimitMb,
        @Valid Options options
) {
}
