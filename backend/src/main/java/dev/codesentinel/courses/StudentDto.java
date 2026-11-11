package dev.codesentinel.courses;

import java.time.Instant;

/**
 * Domain projection for course-enrolled student information.
 */
public record StudentDto(long id, String displayName, String email, Instant enrolledAt) {
}
