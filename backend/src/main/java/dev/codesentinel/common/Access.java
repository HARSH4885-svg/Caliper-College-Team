package dev.codesentinel.common;

import dev.codesentinel.auth.Principal;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Central backend authorization checks. Unknown and forbidden resources both answer 404 to avoid leaking existence. */
@Component
public class Access {
    private final JdbcClient jdbc;

    public Access(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public boolean ownsCourse(long courseId, Principal p) {
        return p.instructor() && jdbc.sql("SELECT teaches(?, ?)").params(courseId, p.id()).query(Boolean.class).single();
    }

    public boolean enrolled(long courseId, Principal p) {
        return !p.instructor() && jdbc.sql("SELECT count(*) FROM enrollments e JOIN courses c ON c.id=e.course_id WHERE e.course_id=? AND e.student_id=? AND NOT c.archived").params(courseId, p.id()).query(Long.class).single() > 0;
    }

    /** Owning instructor or enrolled student. */
    public void requireCourseMember(long courseId, Principal p) {
        if (!ownsCourse(courseId, p) && !enrolled(courseId, p)) throw ApiException.notFound();
    }

    public void requireCourseOwner(long courseId, Principal p) {
        if (!ownsCourse(courseId, p)) throw ApiException.notFound();
    }

    public long courseOfAssignment(long assignmentId) {
        return jdbc.sql("SELECT course_id FROM assignments WHERE id=?").param(assignmentId).query(Long.class).optional().orElseThrow(ApiException::notFound);
    }

    /** Instructor of the course, or an enrolled student when the assignment is published. */
    public long requireAssignmentVisible(long assignmentId, Principal p) {
        long cid = courseOfAssignment(assignmentId);
        if (ownsCourse(cid, p)) return cid;
        if (enrolled(cid, p) && jdbc.sql("SELECT a.published AND NOT a.archived AND NOT c.archived AND (a.available_from IS NULL OR a.available_from <= now()) FROM assignments a JOIN courses c ON c.id=a.course_id WHERE a.id=?").param(assignmentId).query(Boolean.class).single()) return cid;
        throw ApiException.notFound();
    }

    public long requireAssignmentOwner(long assignmentId, Principal p) {
        long cid = courseOfAssignment(assignmentId);
        requireCourseOwner(cid, p);
        return cid;
    }
}
