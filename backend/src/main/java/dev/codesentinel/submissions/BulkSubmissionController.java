package dev.codesentinel.submissions;

import dev.codesentinel.auth.CurrentUser;
import dev.codesentinel.auth.Principal;
import dev.codesentinel.common.Access;
import dev.codesentinel.common.ApiException;
import dev.codesentinel.common.AuditService;
import dev.codesentinel.reports.InsightsController;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Instructor-only bulk actions over an assignment's submissions: re-run the latest attempts, and export them. */
@RestController
@RequestMapping("/api")
public class BulkSubmissionController {
    private static final Set<String> VERDICTS = Set.of("AC", "WA", "CE", "RE", "TLE", "MLE", "IE");
    private final JdbcClient jdbc;
    private final Access access;
    private final AuditService audit;

    public BulkSubmissionController(JdbcClient jdbc, Access access, AuditService audit) {
        this.jdbc = jdbc;
        this.access = access;
        this.audit = audit;
    }

    /**
     * Queues a fresh evaluation for each student's latest attempt. {@code verdict} narrows it to one result (for example IE after
     * a Docker hiccup, or WA after correcting a test); {@code all} re-runs every latest attempt. Attempts already waiting are skipped.
     */
    @PostMapping("/assignments/{assignmentId}/rejudge")
    @Transactional
    Map<String, Object> rejudge(@PathVariable long assignmentId, @RequestParam String verdict) {
        Principal p = CurrentUser.instructor();
        access.requireAssignmentOwner(assignmentId, p);
        boolean all = verdict.equals("all");
        if (!all && !VERDICTS.contains(verdict)) throw ApiException.bad("verdict must be one of AC, WA, CE, RE, TLE, MLE, IE or all");
        List<Long> ids = jdbc.sql("""
                SELECT l.id FROM (SELECT DISTINCT ON (student_id) id, status FROM submissions WHERE assignment_id=? ORDER BY student_id, attempt DESC) l
                LEFT JOIN evaluation_results r ON r.submission_id=l.id
                WHERE l.status IN ('DONE','FAILED') AND (?::boolean OR r.verdict=?) LIMIT 500""")
                .params(assignmentId, all, verdict).query(Long.class).list();
        for (long id : ids) {
            jdbc.sql("DELETE FROM evaluation_results WHERE submission_id=?").param(id).update();
            jdbc.sql("UPDATE submissions SET status='QUEUED' WHERE id=?").param(id).update();
            jdbc.sql("INSERT INTO evaluation_jobs(submission_id) VALUES (?)").param(id).update();
        }
        audit.log(p.id(), "ASSIGNMENT_REJUDGE", "assignment", assignmentId, verdict + ": " + ids.size() + " queued");
        return Map.of("queued", ids.size());
    }

    @GetMapping("/assignments/{assignmentId}/submissions.csv")
    ResponseEntity<byte[]> csv(@PathVariable long assignmentId) {
        Principal p = CurrentUser.instructor();
        access.requireAssignmentOwner(assignmentId, p);
        StringBuilder sb = new StringBuilder("Student,Email,Attempt,Submitted at (UTC),Status,Verdict,Tests passed,Tests total\r\n");
        jdbc.sql("""
                SELECT u.display_name, u.email, s.attempt, s.created_at, s.status, r.verdict, r.passed, r.total
                FROM submissions s JOIN users u ON u.id=s.student_id LEFT JOIN evaluation_results r ON r.submission_id=s.id
                WHERE s.assignment_id=? ORDER BY u.display_name, s.attempt""").param(assignmentId).query((rs, n) -> {
            sb.append(InsightsController.csv(rs.getString(1))).append(',').append(InsightsController.csv(rs.getString(2))).append(',').append(rs.getInt(3)).append(',')
                    .append(rs.getTimestamp(4).toInstant()).append(',').append(rs.getString(5)).append(',').append(InsightsController.csv(rs.getString(6))).append(',')
                    .append(rs.getString(7) == null ? "" : rs.getInt(7)).append(',').append(rs.getString(8) == null ? "" : rs.getInt(8)).append("\r\n");
            return 0;
        }).list();
        audit.log(p.id(), "SUBMISSIONS_EXPORT", "assignment", assignmentId, null);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv; charset=utf-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("submissions-assignment-" + assignmentId + ".csv").build().toString())
                .header("X-Content-Type-Options", "nosniff").body(sb.toString().getBytes(StandardCharsets.UTF_8));
    }
}
