package dev.codesentinel.courses;

import dev.codesentinel.auth.Principal;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.*;

@Repository
public class JdbcCourseRepository implements CourseRepository {

    private final JdbcClient jdbc;

    public JdbcCourseRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private static String nz(String s) {
        return s == null ? "" : s.trim();
    }

    @Override
    public Optional<CourseDto> findDtoById(long id, Principal p) {
        return jdbc.sql("""
                SELECT c.id, c.code, c.title, u.display_name, c.enrollment_code, c.instructor_id, c.synthetic, c.archived,
                  (SELECT count(*) FROM enrollments e WHERE e.course_id=c.id),
                  (SELECT count(*) FROM assignments a WHERE a.course_id=c.id AND NOT a.archived AND (teaches(c.id, ?) OR (a.published AND (a.available_from IS NULL OR a.available_from <= now())))),
                  c.description, c.subject, c.category, c.starts_on, c.ends_on, teaches(c.id, ?),
                  (SELECT coalesce(string_agg(su.display_name, '|' ORDER BY su.display_name), '') FROM course_staff cs JOIN users su ON su.id=cs.user_id WHERE cs.course_id=c.id)
                FROM courses c JOIN users u ON u.id=c.instructor_id WHERE c.id=?""").params(p.id(), p.id(), id)
                .query((rs, n) -> {
                    boolean staff = p.instructor() && rs.getBoolean(16);
                    String others = rs.getString(17);
                    return new CourseDto(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), staff ? rs.getString(5) : null,
                            rs.getInt(9), rs.getInt(10), rs.getBoolean(7), rs.getBoolean(8), rs.getString(11), rs.getString(12), rs.getString(13),
                            rs.getObject(14, LocalDate.class), rs.getObject(15, LocalDate.class), staff && rs.getLong(6) == p.id(),
                            others.isEmpty() ? List.of() : List.of(others.split("\\|")));
                }).optional();
    }

    @Override
    public List<CourseDto> listDtos(Principal p, boolean includeArchived) {
        List<Long> ids = p.instructor()
                ? jdbc.sql("SELECT id FROM courses c WHERE teaches(c.id, ?) AND (?::boolean OR NOT archived) ORDER BY c.code").params(p.id(), includeArchived).query(Long.class).list()
                : jdbc.sql("SELECT e.course_id FROM enrollments e JOIN courses c ON c.id=e.course_id WHERE e.student_id=? AND NOT c.archived ORDER BY e.course_id DESC").param(p.id()).query(Long.class).list();
        return ids.stream().map(i -> findDtoById(i, p).orElse(null)).filter(Objects::nonNull).toList();
    }

    @Override
    public long insert(String code, String title, String enrollmentCode, long instructorId,
                       String description, String subject, String category, LocalDate startsOn, LocalDate endsOn) {
        return jdbc.sql("INSERT INTO courses(code, title, enrollment_code, instructor_id, description, subject, category, starts_on, ends_on) VALUES (?,?,?,?,?,?,?,?,?) RETURNING id")
                .params(code.trim(), title.trim(), enrollmentCode, instructorId, nz(description), nz(subject), nz(category), startsOn, endsOn)
                .query(Long.class).single();
    }

    @Override
    public void updateCourseFields(long courseId, PatchCourse r, String newEnrollmentCode) {
        if (r.code() != null && !r.code().isBlank()) jdbc.sql("UPDATE courses SET code=? WHERE id=?").params(r.code().trim(), courseId).update();
        if (r.title() != null && !r.title().isBlank()) jdbc.sql("UPDATE courses SET title=? WHERE id=?").params(r.title().trim(), courseId).update();
        if (r.description() != null) jdbc.sql("UPDATE courses SET description=? WHERE id=?").params(r.description().trim(), courseId).update();
        if (r.subject() != null) jdbc.sql("UPDATE courses SET subject=? WHERE id=?").params(r.subject().trim(), courseId).update();
        if (r.category() != null) jdbc.sql("UPDATE courses SET category=? WHERE id=?").params(r.category().trim(), courseId).update();
        if (r.startsOn() != null) jdbc.sql("UPDATE courses SET starts_on=? WHERE id=?").params(r.startsOn(), courseId).update();
        if (r.endsOn() != null) jdbc.sql("UPDATE courses SET ends_on=? WHERE id=?").params(r.endsOn(), courseId).update();
        if (newEnrollmentCode != null) jdbc.sql("UPDATE courses SET enrollment_code=? WHERE id=?").params(newEnrollmentCode, courseId).update();
    }

    @Override
    public LocalDate[] getCourseDates(long courseId) {
        return jdbc.sql("SELECT starts_on, ends_on FROM courses WHERE id=?").param(courseId)
                .query((rs, n) -> new LocalDate[]{rs.getObject(1, LocalDate.class), rs.getObject(2, LocalDate.class)}).single();
    }

    @Override
    public void setArchived(long courseId, boolean archived) {
        jdbc.sql("UPDATE courses SET archived=? WHERE id=?").params(archived, courseId).update();
    }

    @Override
    public boolean isEnrollmentCodeValid(long courseId, String enrollmentCode) {
        return jdbc.sql("SELECT count(*) FROM courses WHERE id=? AND enrollment_code=? AND NOT archived")
                .params(courseId, enrollmentCode.trim()).query(Long.class).single() > 0;
    }

    @Override
    public Optional<Long> findCourseIdByEnrollmentCode(String enrollmentCode) {
        return jdbc.sql("SELECT id FROM courses WHERE enrollment_code=? AND NOT archived")
                .param(enrollmentCode.trim()).query(Long.class).optional();
    }

    @Override
    public void enrollStudent(long courseId, long studentId) {
        jdbc.sql("INSERT INTO enrollments(course_id, student_id) VALUES (?,?) ON CONFLICT DO NOTHING")
                .params(courseId, studentId).update();
    }

    @Override
    public List<StudentDto> listStudents(long courseId) {
        return jdbc.sql("SELECT u.id, u.display_name, u.email, e.created_at FROM enrollments e JOIN users u ON u.id=e.student_id WHERE e.course_id=? ORDER BY u.display_name")
                .param(courseId).query((rs, n) -> new StudentDto(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getTimestamp(4).toInstant())).list();
    }

    @Override
    public long getLeadInstructorId(long courseId) {
        return jdbc.sql("SELECT instructor_id FROM courses WHERE id=?").param(courseId).query(Long.class).single();
    }

    @Override
    public List<StaffDto> listStaff(long courseId, boolean isInstructor) {
        return jdbc.sql("""
                SELECT u.id, u.display_name, u.email, 'LEAD' FROM courses c JOIN users u ON u.id=c.instructor_id WHERE c.id=?
                UNION ALL SELECT u.id, u.display_name, u.email, cs.role FROM course_staff cs JOIN users u ON u.id=cs.user_id WHERE cs.course_id=? ORDER BY 4, 2""")
                .params(courseId, courseId).query((rs, n) -> new StaffDto(rs.getLong(1), rs.getString(2), isInstructor ? rs.getString(3) : null, rs.getString(4))).list();
    }

    @Override
    public Optional<Object[]> findUserForStaff(String email) {
        return jdbc.sql("SELECT id, role FROM users WHERE lower(email)=lower(?) AND enabled").param(email.trim())
                .query((rs, n) -> new Object[]{rs.getLong(1), rs.getString(2)}).optional();
    }

    @Override
    public void upsertStaff(long courseId, long userId, String role) {
        jdbc.sql("INSERT INTO course_staff(course_id, user_id, role) VALUES (?,?,?) ON CONFLICT (course_id, user_id) DO UPDATE SET role=EXCLUDED.role")
                .params(courseId, userId, role == null ? "CO_INSTRUCTOR" : role).update();
    }

    @Override
    public void deleteStaff(long courseId, long userId) {
        jdbc.sql("DELETE FROM course_staff WHERE course_id=? AND user_id=?").params(courseId, userId).update();
    }

    @Override
    public Map<String, Object> bulkEnroll(long courseId, List<String> emails) {
        List<String> added = new ArrayList<>(), already = new ArrayList<>(), unknown = new ArrayList<>();
        for (String raw : new LinkedHashSet<>(emails)) {
            String e = raw == null ? "" : raw.trim();
            if (e.isEmpty()) continue;
            var id = jdbc.sql("SELECT id FROM users WHERE lower(email)=lower(?) AND role='STUDENT' AND enabled").param(e).query(Long.class).optional();
            if (id.isEmpty()) {
                unknown.add(e);
            } else if (jdbc.sql("INSERT INTO enrollments(course_id, student_id) VALUES (?,?) ON CONFLICT DO NOTHING").params(courseId, id.get()).update() > 0) {
                added.add(e);
            } else {
                already.add(e);
            }
        }
        return Map.of("added", added, "alreadyEnrolled", already, "notFound", unknown);
    }

    @Override
    public boolean unenrollStudent(long courseId, long studentId) {
        return jdbc.sql("DELETE FROM enrollments WHERE course_id=? AND student_id=?").params(courseId, studentId).update() > 0;
    }

    @Override
    public long countCourseSubmissionsAndAttempts(long courseId) {
        return jdbc.sql("SELECT (SELECT count(*) FROM submissions s JOIN assignments a ON a.id=s.assignment_id WHERE a.course_id=?) + (SELECT count(*) FROM quiz_attempts q JOIN assignments a ON a.id=q.assignment_id WHERE a.course_id=?)")
                .params(courseId, courseId).query(Long.class).single();
    }

    @Override
    public void deleteCourseAndAnnouncements(long courseId) {
        jdbc.sql("DELETE FROM announcements WHERE course_id=?").param(courseId).update();
        jdbc.sql("DELETE FROM courses WHERE id=?").param(courseId).update();
    }

    @Override
    public long cloneCourseStructure(long courseId, long newInstructorId, String newEnrollmentCode) {
        long copy = jdbc.sql("""
                INSERT INTO courses(code, title, enrollment_code, instructor_id, description, subject, category)
                SELECT left(code, 25) || '-NEW', left(title, 140) || ' (copy)', ?, ?, description, subject, category FROM courses WHERE id=? RETURNING id""")
                .params(newEnrollmentCode, newInstructorId, courseId).query(Long.class).single();
        var ids = jdbc.sql("SELECT id FROM assignments WHERE course_id=? AND NOT archived ORDER BY id").param(courseId).query(Long.class).list();
        for (long a : ids) {
            long na = jdbc.sql("""
                    INSERT INTO assignments(course_id, title, description, language, deadline, time_limit_ms, memory_limit_mb, kind, category, difficulty, max_marks, passing_marks,
                        max_attempts, late_policy, late_penalty_pct, quiz_minutes, shuffle_questions, shuffle_options, release_mode, show_answers)
                    SELECT ?, title, description, language, now() + interval '30 days', time_limit_ms, memory_limit_mb, kind, category, difficulty, max_marks, passing_marks,
                        max_attempts, late_policy, late_penalty_pct, quiz_minutes, shuffle_questions, shuffle_options, release_mode, show_answers FROM assignments WHERE id=? RETURNING id""")
                    .params(copy, a).query(Long.class).single();
            jdbc.sql("INSERT INTO test_cases(assignment_id, name, input, expected_output, sample, ord) SELECT ?, name, input, expected_output, sample, ord FROM test_cases WHERE assignment_id=?").params(na, a).update();
            jdbc.sql("INSERT INTO questions(assignment_id, ord, type, prompt, marks, difficulty, required, options, answer, settings, explanation, bank_id) SELECT ?, ord, type, prompt, marks, difficulty, required, options, answer, settings, explanation, bank_id FROM questions WHERE assignment_id=?").params(na, a).update();
        }
        return copy;
    }
}
