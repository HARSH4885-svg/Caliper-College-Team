package dev.codesentinel.courses;

import dev.codesentinel.auth.CurrentUser;
import dev.codesentinel.auth.Principal;
import dev.codesentinel.common.Access;
import dev.codesentinel.common.ApiException;
import dev.codesentinel.common.AuditService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * REST controller for Course management.
 * Adheres to the Single Responsibility Principle (SRP) by delegating persistence to {@link CourseRepository}.
 */
@RestController
@RequestMapping("/api/courses")
public class CourseController {

    record CreateCourse(@NotBlank @Size(max = 30) String code, @NotBlank @Size(max = 150) String title, @Size(max = 5000) String description,
                        @Size(max = 80) String subject, @Size(max = 60) String category, LocalDate startsOn, LocalDate endsOn) {
    }

    record StaffRequest(@NotBlank @Size(max = 254) String email, @jakarta.validation.constraints.Pattern(regexp = "CO_INSTRUCTOR|TA") String role) {
    }

    record BulkEnroll(@jakarta.validation.constraints.NotNull @Size(max = 1000) List<@Size(max = 254) String> emails) {
    }

    record EnrollRequest(@NotBlank @Size(max = 40) String enrollmentCode) {
    }

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private final CourseRepository repository;
    private final Access access;
    private final AuditService audit;
    private final SecureRandom rnd = new SecureRandom();

    public CourseController(CourseRepository repository, Access access, AuditService audit) {
        this.repository = repository;
        this.access = access;
        this.audit = audit;
    }

    private String newCode() {
        StringBuilder sb = new StringBuilder("CS-");
        for (int i = 0; i < 10; i++) sb.append(ALPHABET.charAt(rnd.nextInt(ALPHABET.length())));
        return sb.toString();
    }

    private CourseDto dto(long id, Principal p) {
        return repository.findDtoById(id, p).orElseThrow(ApiException::notFound);
    }

    private void checkDates(LocalDate a, LocalDate b) {
        if (a != null && b != null && b.isBefore(a)) throw ApiException.bad("The course cannot end before it starts");
    }

    @GetMapping
    List<CourseDto> list(@RequestParam(defaultValue = "false") boolean includeArchived) {
        Principal p = CurrentUser.get();
        return repository.listDtos(p, includeArchived);
    }

    @PostMapping
    CourseDto create(@Valid @RequestBody CreateCourse r) {
        Principal p = CurrentUser.instructor();
        checkDates(r.startsOn(), r.endsOn());
        long id = repository.insert(r.code().trim(), r.title().trim(), newCode(), p.id(),
                r.description(), r.subject(), r.category(), r.startsOn(), r.endsOn());
        audit.log(p.id(), "COURSE_CREATE", "course", id, r.code());
        return dto(id, p);
    }

    @GetMapping("/{courseId}")
    CourseDto get(@PathVariable long courseId) {
        Principal p = CurrentUser.get();
        access.requireCourseMember(courseId, p);
        return dto(courseId, p);
    }

    @PatchMapping("/{courseId}")
    CourseDto patch(@PathVariable long courseId, @Valid @RequestBody PatchCourse r) {
        Principal p = CurrentUser.instructor();
        access.requireCourseOwner(courseId, p);
        String newCode = Boolean.TRUE.equals(r.rotateEnrollmentCode()) ? newCode() : null;
        repository.updateCourseFields(courseId, r, newCode);
        LocalDate[] dates = repository.getCourseDates(courseId);
        checkDates(dates[0], dates[1]);
        audit.log(p.id(), "COURSE_UPDATE", "course", courseId, null);
        return dto(courseId, p);
    }

    /** Archiving hides the course and its assignments from students and stops submissions; nothing is deleted. */
    @PostMapping("/{courseId}/archive")
    CourseDto archive(@PathVariable long courseId, @RequestParam(defaultValue = "true") boolean archived) {
        Principal p = CurrentUser.instructor();
        access.requireCourseOwner(courseId, p);
        repository.setArchived(courseId, archived);
        audit.log(p.id(), archived ? "COURSE_ARCHIVE" : "COURSE_UNARCHIVE", "course", courseId, null);
        return dto(courseId, p);
    }

    @PostMapping("/{courseId}/enroll")
    CourseDto enroll(@PathVariable long courseId, @Valid @RequestBody EnrollRequest r) {
        Principal p = CurrentUser.get();
        if (p.instructor()) throw ApiException.forbidden();
        boolean ok = repository.isEnrollmentCodeValid(courseId, r.enrollmentCode().trim());
        if (!ok) throw ApiException.bad("Invalid enrollment code");
        repository.enrollStudent(courseId, p.id());
        audit.log(p.id(), "ENROLL", "course", courseId, null);
        return dto(courseId, p);
    }

    /** Join by code alone (the instructor shares only the code); the course is found from it. */
    @PostMapping("/join")
    CourseDto join(@Valid @RequestBody EnrollRequest r) {
        Principal p = CurrentUser.get();
        if (p.instructor()) throw ApiException.forbidden();
        Long courseId = repository.findCourseIdByEnrollmentCode(r.enrollmentCode().trim())
                .orElseThrow(() -> ApiException.bad("Invalid enrollment code"));
        repository.enrollStudent(courseId, p.id());
        audit.log(p.id(), "ENROLL", "course", courseId, null);
        return dto(courseId, p);
    }

    @GetMapping("/{courseId}/students")
    List<StudentDto> students(@PathVariable long courseId) {
        Principal p = CurrentUser.instructor();
        access.requireCourseOwner(courseId, p);
        return repository.listStudents(courseId);
    }

    private void requireLead(long courseId, Principal p) {
        access.requireCourseOwner(courseId, p);
        long lead = repository.getLeadInstructorId(courseId);
        if (lead != p.id()) throw ApiException.forbidden();
    }

    @GetMapping("/{courseId}/staff")
    List<StaffDto> staff(@PathVariable long courseId) {
        Principal p = CurrentUser.get();
        access.requireCourseMember(courseId, p);
        return repository.listStaff(courseId, p.instructor());
    }

    /** The lead instructor adds another instructor account as co-instructor or teaching assistant. */
    @PostMapping("/{courseId}/staff")
    List<StaffDto> addStaff(@PathVariable long courseId, @Valid @RequestBody StaffRequest r) {
        Principal p = CurrentUser.instructor();
        requireLead(courseId, p);
        Object[] u = repository.findUserForStaff(r.email().trim())
                .orElseThrow(() -> ApiException.bad("No instructor account has that email"));
        if (!"INSTRUCTOR".equals(u[1])) throw ApiException.bad("Only instructor accounts can teach a course");
        if ((Long) u[0] == p.id()) throw ApiException.bad("You already lead this course");
        repository.upsertStaff(courseId, (Long) u[0], r.role());
        audit.log(p.id(), "STAFF_ADD", "course", courseId, "user " + u[0]);
        return staff(courseId);
    }

    @DeleteMapping("/{courseId}/staff/{userId}")
    List<StaffDto> removeStaff(@PathVariable long courseId, @PathVariable long userId) {
        Principal p = CurrentUser.instructor();
        requireLead(courseId, p);
        repository.deleteStaff(courseId, userId);
        audit.log(p.id(), "STAFF_REMOVE", "course", courseId, "user " + userId);
        return staff(courseId);
    }

    /** Enrols existing student accounts by email (for example pasted from a spreadsheet column). Nobody's account is created here. */
    @PostMapping("/{courseId}/enrollments")
    Map<String, Object> bulkEnroll(@PathVariable long courseId, @Valid @RequestBody BulkEnroll r) {
        Principal p = CurrentUser.instructor();
        access.requireCourseOwner(courseId, p);
        Map<String, Object> result = repository.bulkEnroll(courseId, r.emails());
        audit.log(p.id(), "ENROLL_BULK", "course", courseId, ((List<?>) result.get("added")).size() + " added");
        return result;
    }

    /** Removes a student from the roster. Their submissions stay (they are part of the record) but they lose access. */
    @DeleteMapping("/{courseId}/students/{studentId}")
    ResponseEntity<Void> unenroll(@PathVariable long courseId, @PathVariable long studentId) {
        Principal p = CurrentUser.instructor();
        access.requireCourseOwner(courseId, p);
        if (!repository.unenrollStudent(courseId, studentId)) throw ApiException.notFound();
        audit.log(p.id(), "UNENROLL", "course", courseId, "student " + studentId);
        return ResponseEntity.noContent().build();
    }

    /** Only an empty course can be deleted; once students have handed in work, archive it instead so the record survives. */
    @DeleteMapping("/{courseId}")
    ResponseEntity<Void> delete(@PathVariable long courseId) {
        Principal p = CurrentUser.instructor();
        requireLead(courseId, p);
        long work = repository.countCourseSubmissionsAndAttempts(courseId);
        if (work > 0) throw ApiException.conflict("Students have submitted work in this course. Archive it instead; deleting would destroy their record.");
        repository.deleteCourseAndAnnouncements(courseId);
        audit.log(p.id(), "COURSE_DELETE", "course", courseId, null);
        return ResponseEntity.noContent().build();
    }

    /** Copies a course's structure (details, assignments as drafts, tests, questions) for a new term. No students or work are copied. */
    @PostMapping("/{courseId}/clone")
    @Transactional
    CourseDto cloneCourse(@PathVariable long courseId) {
        Principal p = CurrentUser.instructor();
        access.requireCourseOwner(courseId, p);
        long copy = repository.cloneCourseStructure(courseId, p.id(), newCode());
        audit.log(p.id(), "COURSE_CLONE", "course", copy, "from " + courseId);
        return dto(copy, p);
    }
}
