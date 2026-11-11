package dev.codesentinel.courses;

import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Command payload for updating course details.
 */
public record PatchCourse(
        @Size(max = 30) String code,
        @Size(max = 150) String title,
        Boolean rotateEnrollmentCode,
        @Size(max = 5000) String description,
        @Size(max = 80) String subject,
        @Size(max = 60) String category,
        LocalDate startsOn,
        LocalDate endsOn
) {
}
