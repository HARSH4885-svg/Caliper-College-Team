package dev.codesentinel.reports;

import dev.codesentinel.auth.CurrentUser;
import dev.codesentinel.auth.Principal;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/** Every number comes from a query; nothing here is hard-coded. */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    public record InstructorDashboard(int courses, int publishedAssignments, int submissions, int pendingEvaluations, int pairsNeedingReview, List<AssignmentRow> assignments) {
    }

    public record AssignmentRow(long assignmentId, long courseId, String courseCode, String title, Instant deadline, boolean published, int submissions, int pending, int reviewPairs, int toMark, String kind) {
    }

    public record StudentDashboard(List<DeadlineRow> upcoming, List<RecentRow> recent) {
    }

    public record DeadlineRow(long assignmentId, long courseId, String courseCode, String title, Instant deadline, boolean submitted) {
    }

    public record RecentRow(long submissionId, long assignmentId, String assignmentTitle, int attempt, String status, String verdict, Instant createdAt) {
    }

    private final JdbcClient jdbc;

    public DashboardController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/instructor")
    InstructorDashboard instructor() {
        Principal p = CurrentUser.instructor();
        int courses = jdbc.sql("SELECT count(*) FROM courses WHERE teaches(id, ?) AND NOT archived").param(p.id()).query(Integer.class).single();
        List<AssignmentRow> rows = jdbc.sql("""
                SELECT a.id, c.id, c.code, a.title, a.deadline, a.published,
                  (SELECT count(*) FROM submissions s WHERE s.assignment_id=a.id) + (SELECT count(*) FROM quiz_attempts q WHERE q.assignment_id=a.id AND q.status<>'IN_PROGRESS'),
                  (SELECT count(*) FROM submissions s WHERE s.assignment_id=a.id AND s.status IN ('QUEUED','RUNNING')),
                  (SELECT count(*) FROM similarity_pairs sp WHERE sp.run_id=(SELECT max(id) FROM plagiarism_runs r WHERE r.assignment_id=a.id)
                     AND sp.state IN ('NEEDS_REVIEW','HIGH_PRIORITY_REVIEW')
                     AND NOT EXISTS (SELECT 1 FROM plagiarism_reviews pr JOIN similarity_pairs q ON q.id=pr.pair_id WHERE q.submission_a=sp.submission_a AND q.submission_b=sp.submission_b)),
                  (SELECT count(*) FROM quiz_attempts q WHERE q.assignment_id=a.id AND q.status='SUBMITTED'), a.kind
                FROM assignments a JOIN courses c ON c.id=a.course_id WHERE teaches(c.id, ?) AND NOT a.archived AND NOT c.archived ORDER BY a.deadline DESC""").param(p.id())
                .query((rs, n) -> new AssignmentRow(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4), rs.getTimestamp(5).toInstant(), rs.getBoolean(6), rs.getInt(7), rs.getInt(8), rs.getInt(9), rs.getInt(10), rs.getString(11))).list();
        return new InstructorDashboard(courses, (int) rows.stream().filter(AssignmentRow::published).count(), rows.stream().mapToInt(AssignmentRow::submissions).sum(),
                rows.stream().mapToInt(AssignmentRow::pending).sum(), rows.stream().mapToInt(AssignmentRow::reviewPairs).sum(), rows);
    }

    @GetMapping("/student")
    StudentDashboard student() {
        Principal p = CurrentUser.get();
        if (p.instructor()) throw dev.codesentinel.common.ApiException.forbidden();
        var upcoming = jdbc.sql("""
                SELECT a.id, c.id, c.code, a.title, a.deadline,
                  EXISTS (SELECT 1 FROM submissions s WHERE s.assignment_id=a.id AND s.student_id=?) OR EXISTS (SELECT 1 FROM quiz_attempts q WHERE q.assignment_id=a.id AND q.student_id=e.student_id AND q.status<>'IN_PROGRESS')
                FROM assignments a JOIN courses c ON c.id=a.course_id JOIN enrollments e ON e.course_id=c.id AND e.student_id=?
                WHERE a.published AND NOT a.archived AND NOT c.archived AND a.deadline > now() AND (a.available_from IS NULL OR a.available_from <= now()) ORDER BY a.deadline LIMIT 20""").params(p.id(), p.id())
                .query((rs, n) -> new DeadlineRow(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4), rs.getTimestamp(5).toInstant(), rs.getBoolean(6))).list();
        var recent = jdbc.sql("""
                SELECT s.id, a.id, a.title, s.attempt, s.status, r.verdict, s.created_at FROM submissions s JOIN assignments a ON a.id=s.assignment_id
                LEFT JOIN evaluation_results r ON r.submission_id=s.id WHERE s.student_id=? ORDER BY s.created_at DESC LIMIT 10""").param(p.id())
                .query((rs, n) -> new RecentRow(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getInt(4), rs.getString(5), rs.getString(6), rs.getTimestamp(7).toInstant())).list();
        return new StudentDashboard(upcoming, recent);
    }
}
