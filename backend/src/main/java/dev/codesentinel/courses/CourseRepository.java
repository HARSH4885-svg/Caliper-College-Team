package dev.codesentinel.courses;

import dev.codesentinel.auth.Principal;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Data access abstraction for Course persistence and management.
 * Adheres to the Single Responsibility Principle (SRP) and Dependency Inversion Principle (DIP).
 */
public interface CourseRepository {

    Optional<CourseDto> findDtoById(long id, Principal p);

    List<CourseDto> listDtos(Principal p, boolean includeArchived);

    long insert(String code, String title, String enrollmentCode, long instructorId,
                String description, String subject, String category, LocalDate startsOn, LocalDate endsOn);

    void updateCourseFields(long courseId, PatchCourse r, String newEnrollmentCode);

    LocalDate[] getCourseDates(long courseId);

    void setArchived(long courseId, boolean archived);

    boolean isEnrollmentCodeValid(long courseId, String enrollmentCode);

    Optional<Long> findCourseIdByEnrollmentCode(String enrollmentCode);

    void enrollStudent(long courseId, long studentId);

    List<StudentDto> listStudents(long courseId);

    long getLeadInstructorId(long courseId);

    List<StaffDto> listStaff(long courseId, boolean isInstructor);

    Optional<Object[]> findUserForStaff(String email);

    void upsertStaff(long courseId, long userId, String role);

    void deleteStaff(long courseId, long userId);

    Map<String, Object> bulkEnroll(long courseId, List<String> emails);

    boolean unenrollStudent(long courseId, long studentId);

    long countCourseSubmissionsAndAttempts(long courseId);

    void deleteCourseAndAnnouncements(long courseId);

    long cloneCourseStructure(long courseId, long newInstructorId, String newEnrollmentCode);
}
