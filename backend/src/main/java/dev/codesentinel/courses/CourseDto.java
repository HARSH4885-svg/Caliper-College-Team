package dev.codesentinel.courses;

import java.time.LocalDate;
import java.util.List;

public record CourseDto(
        long id,
        String code,
        String title,
        String instructorName,
        String enrollmentCode,
        int students,
        int assignments,
        boolean synthetic,
        boolean archived,
        String description,
        String subject,
        String category,
        LocalDate startsOn,
        LocalDate endsOn,
        boolean lead,
        List<String> staff
) {
}
