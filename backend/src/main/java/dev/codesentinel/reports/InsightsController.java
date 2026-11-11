package dev.codesentinel.reports;

import dev.codesentinel.auth.CurrentUser;
import dev.codesentinel.auth.Principal;
import dev.codesentinel.common.Access;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Instructor-only read models: how an assignment is going (verdicts, score spread, which tests students fail) and a
 * course gradebook (best result per student per assignment) with CSV export. Pure aggregation over data the instructor
 * can already open one by one; nothing here changes grades or notifies anyone.
 */
@RestController
@RequestMapping("/api")
public class InsightsController {
    private final JdbcClient jdbc;
    private final Access access;

    public InsightsController(JdbcClient jdbc, Access access) {
        this.jdbc = jdbc;
        this.access = access;
    }

    public record Bucket(String label, long count) {
    }

    public record TestStat(String name, boolean sample, long passed, long total) {
    }

    public record Analytics(long enrolled, long submitted, long pending, double averageAttempts, Map<String, Long> verdicts,
                            List<Bucket> scoreBuckets, List<TestStat> tests, Long medianMillis) {
    }

    private static final String LATEST = "SELECT DISTINCT ON (s.student_id) s.id, s.student_id, s.attempt, s.status FROM submissions s WHERE s.assignment_id=? ORDER BY s.student_id, s.attempt DESC";

    @GetMapping("/assignments/{assignmentId}/analytics")
    Analytics analytics(@PathVariable long assignmentId) {
        Principal p = CurrentUser.instructor();
        long courseId = access.requireAssignmentOwner(assignmentId, p);
        long enrolled = jdbc.sql("SELECT count(*) FROM enrollments WHERE course_id=?").param(courseId).query(Long.class).single();
        long submitted = jdbc.sql("SELECT count(DISTINCT student_id) FROM submissions WHERE assignment_id=?").param(assignmentId).query(Long.class).single();
        Double avg = jdbc.sql("SELECT avg(c) FROM (SELECT count(*) c FROM submissions WHERE assignment_id=? GROUP BY student_id) t").param(assignmentId).query(Double.class).optional().orElse(0.0);
        long pending = jdbc.sql("SELECT count(*) FROM (" + LATEST + ") l WHERE l.status IN ('QUEUED','RUNNING')").param(assignmentId).query(Long.class).single();

        Map<String, Long> verdicts = new LinkedHashMap<>();
        for (String v : List.of("AC", "WA", "CE", "RE", "TLE", "MLE", "IE")) verdicts.put(v, 0L);
        long[] b = new long[6];
        List<Long> millis = new ArrayList<>();
        jdbc.sql("SELECT r.verdict, r.passed, r.total, r.millis FROM (" + LATEST + ") l JOIN evaluation_results r ON r.submission_id=l.id").param(assignmentId).query((rs, n) -> {
            verdicts.merge(rs.getString(1), 1L, Long::sum);
            int passed = rs.getInt(2), total = rs.getInt(3);
            double pct = total == 0 ? 0 : 100.0 * passed / total;
            b[pct >= 100 ? 5 : pct >= 75 ? 4 : pct >= 50 ? 3 : pct >= 25 ? 2 : pct > 0 ? 1 : 0]++;
            millis.add(rs.getLong(4));
            return 0;
        }).list();
        java.util.Collections.sort(millis);
        List<Bucket> buckets = List.of(new Bucket("0%", b[0]), new Bucket("1-24%", b[1]), new Bucket("25-49%", b[2]), new Bucket("50-74%", b[3]), new Bucket("75-99%", b[4]), new Bucket("100%", b[5]));

        List<TestStat> tests = jdbc.sql("""
                SELECT t.name, t.sample, count(*) FILTER (WHERE t.verdict='AC'), count(*)
                FROM (""" + LATEST + """
                ) l JOIN evaluation_results r ON r.submission_id=l.id JOIN testcase_results t ON t.result_id=r.id
                GROUP BY t.name, t.sample ORDER BY min(t.id)""").param(assignmentId)
                .query((rs, n) -> new TestStat(rs.getString(1), rs.getBoolean(2), rs.getLong(3), rs.getLong(4))).list();
        return new Analytics(enrolled, submitted, pending, avg == null ? 0 : Math.round(avg * 10) / 10.0, verdicts, buckets, tests, millis.isEmpty() ? null : millis.get(millis.size() / 2));
    }

    /**
     * One student's standing on one assignment. {@code score} is the best mark (coding: the instructor's override, else max marks x
     * share of tests passed; quizzes: the best submitted attempt), after any late penalty. {@code status} is GRADED, PENDING
     * (waiting for the judge or a marker), IN_PROGRESS, RETURNED, MISSING (past due, nothing handed in) or NOT_STARTED.
     */
    public record Cell(long assignmentId, String verdict, int passed, int total, int attempts, BigDecimal score, BigDecimal maxMarks, boolean late, String status) {
    }

    public record Row(long studentId, String name, String email, List<Cell> cells, BigDecimal totalScore, BigDecimal totalMax, Double percent, int completed, int missing, int late) {
    }

    public record GradeAssignment(long id, String title, String kind, BigDecimal maxMarks, java.time.Instant deadline) {
    }

    public record Gradebook(List<GradeAssignment> assignments, List<Row> rows) {
    }

    Gradebook book(long courseId) {
        return book(courseId, null);
    }

    static BigDecimal penalise(BigDecimal score, boolean late, String policy, int pct) {
        if (score == null || !late || !"PENALTY".equals(policy) || pct <= 0) return score;
        return score.multiply(BigDecimal.valueOf(100 - pct)).divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
    }

    /** {@code only} limits the book to one student's row; quiz marks a student may not see yet are then left out. */
    public Gradebook book(long courseId, Long only) {
        record A(long id, String title, String kind, BigDecimal max, java.time.Instant deadline, String policy, int pct, String release, boolean show) {
        }
        List<A> as = jdbc.sql("SELECT id, title, kind, max_marks, deadline, late_policy, late_penalty_pct, release_mode FROM assignments WHERE course_id=? AND published AND NOT archived AND (?::bigint IS NULL OR available_from IS NULL OR available_from <= now()) ORDER BY deadline, id")
                .params(courseId, only).query((rs, n) -> new A(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getBigDecimal(4), rs.getTimestamp(5).toInstant(), rs.getString(6), rs.getInt(7), rs.getString(8), true)).list();
        Map<Long, A> byId = new LinkedHashMap<>();
        as.forEach(a -> byId.put(a.id(), a));
        record Stu(long id, String name, String email) {
        }
        List<Stu> studs = jdbc.sql("SELECT u.id, u.display_name, u.email FROM enrollments e JOIN users u ON u.id=e.student_id WHERE e.course_id=? AND (?::bigint IS NULL OR u.id=?) ORDER BY u.display_name").params(courseId, only, only)
                .query((rs, n) -> new Stu(rs.getLong(1), rs.getString(2), rs.getString(3))).list();
        Map<String, Cell> best = new LinkedHashMap<>();
        java.time.Instant now = java.time.Instant.now();
        // coding: best by score, newest attempt wins ties; attempts counted
        jdbc.sql("""
                SELECT s.student_id, s.assignment_id, s.attempt, r.verdict, r.passed, r.total, s.late, s.override_score, s.status
                FROM submissions s JOIN assignments a ON a.id=s.assignment_id AND a.course_id=? AND a.published AND NOT a.archived AND a.kind='CODING'
                LEFT JOIN evaluation_results r ON r.submission_id=s.id WHERE (?::bigint IS NULL OR s.student_id=?) ORDER BY s.student_id, s.assignment_id, s.attempt""").params(courseId, only, only).query((rs, n) -> {
            A a = byId.get(rs.getLong(2));
            if (a == null) return 0;
            String key = rs.getLong(1) + ":" + a.id();
            String verdict = rs.getString(4);
            int passed = rs.getInt(5), total = rs.getInt(6);
            boolean late = rs.getBoolean(7);
            BigDecimal override = rs.getBigDecimal(8);
            BigDecimal score = override != null ? override : verdict == null ? null
                    : penalise(total == 0 ? BigDecimal.ZERO : a.max().multiply(BigDecimal.valueOf(passed)).divide(BigDecimal.valueOf(total), 2, java.math.RoundingMode.HALF_UP), late, a.policy(), a.pct());
            Cell cur = best.get(key);
            int att = cur == null ? 1 : cur.attempts() + 1;
            boolean better = cur == null || cur.score() == null || (score != null && score.compareTo(cur.score()) >= 0);
            Cell next = better ? new Cell(a.id(), verdict, passed, total, att, score, a.max(), late, score == null ? "PENDING" : "GRADED")
                    : new Cell(cur.assignmentId(), cur.verdict(), cur.passed(), cur.total(), att, cur.score(), cur.maxMarks(), cur.late(), cur.status());
            best.put(key, next);
            return 0;
        }).list();
        // quizzes: best submitted attempt; a student only sees released marks
        jdbc.sql("""
                SELECT q.student_id, q.assignment_id, q.status, q.score, q.late, q.released FROM quiz_attempts q JOIN assignments a ON a.id=q.assignment_id
                WHERE a.course_id=? AND a.published AND NOT a.archived AND (?::bigint IS NULL OR q.student_id=?) ORDER BY q.student_id, q.assignment_id, q.attempt""").params(courseId, only, only).query((rs, n) -> {
            A a = byId.get(rs.getLong(2));
            if (a == null) return 0;
            String key = rs.getLong(1) + ":" + a.id();
            String st = rs.getString(3);
            boolean released = rs.getBoolean(6) || ("AFTER_DUE".equals(a.release()) && now.isAfter(a.deadline()) && !st.equals("SUBMITTED"));
            BigDecimal score = st.equals("IN_PROGRESS") || st.equals("SUBMITTED") || (only != null && !released) ? null : rs.getBigDecimal(4);
            String status = st.equals("IN_PROGRESS") ? "IN_PROGRESS" : st.equals("RETURNED") ? "RETURNED" : score == null ? "PENDING" : "GRADED";
            Cell cur = best.get(key);
            int att = cur == null ? 1 : cur.attempts() + 1;
            boolean better = cur == null || (score != null && (cur.score() == null || score.compareTo(cur.score()) >= 0)) || (cur.score() == null && !status.equals("IN_PROGRESS"));
            best.put(key, better ? new Cell(a.id(), null, 0, 0, att, score, a.max(), rs.getBoolean(5), status)
                    : new Cell(cur.assignmentId(), null, 0, 0, att, cur.score(), cur.maxMarks(), cur.late(), cur.status()));
            return 0;
        }).list();
        List<Row> rows = new ArrayList<>();
        for (Stu s : studs) {
            List<Cell> cells = new ArrayList<>();
            BigDecimal got = BigDecimal.ZERO, max = BigDecimal.ZERO;
            int completed = 0, missing = 0, late = 0;
            for (A a : as) {
                Cell c = best.get(s.id() + ":" + a.id());
                if (c == null) {
                    boolean due = now.isAfter(a.deadline());
                    if (due) {
                        missing++;
                        max = max.add(a.max()); // a missed deadline counts as zero
                    }
                    cells.add(new Cell(a.id(), null, 0, 0, 0, null, a.max(), false, due ? "MISSING" : "NOT_STARTED"));
                    continue;
                }
                if (c.late()) late++;
                if (c.score() != null) {
                    got = got.add(c.score());
                    max = max.add(a.max());
                    completed++;
                }
                cells.add(c);
            }
            Double pct = max.signum() == 0 ? null : Math.round(got.doubleValue() * 1000 / max.doubleValue()) / 10.0;
            rows.add(new Row(s.id(), s.name(), s.email(), cells, got.setScale(2, java.math.RoundingMode.HALF_UP), max, pct, completed, missing, late));
        }
        return new Gradebook(as.stream().map(a -> new GradeAssignment(a.id(), a.title(), a.kind(), a.max(), a.deadline())).toList(), rows);
    }

    /** A student's own best result per published assignment in a class they are enrolled in. */
    @GetMapping("/courses/{courseId}/my-progress")
    Gradebook myProgress(@PathVariable long courseId) {
        Principal p = CurrentUser.get();
        if (p.instructor()) throw dev.codesentinel.common.ApiException.forbidden();
        access.requireCourseMember(courseId, p);
        return book(courseId, p.id());
    }

    @GetMapping("/courses/{courseId}/gradebook")
    Gradebook gradebook(@PathVariable long courseId) {
        access.requireCourseOwner(courseId, CurrentUser.instructor());
        return book(courseId);
    }

    @GetMapping("/courses/{courseId}/gradebook.csv")
    ResponseEntity<byte[]> gradebookCsv(@PathVariable long courseId) {
        access.requireCourseOwner(courseId, CurrentUser.instructor());
        Gradebook g = book(courseId);
        StringBuilder sb = new StringBuilder("student,email");
        for (GradeAssignment a : g.assignments()) sb.append(',').append(csv(a.title() + " (score / " + a.maxMarks().stripTrailingZeros().toPlainString() + ")")).append(',').append(csv(a.title() + " (status)"));
        sb.append(",total,out of,percent,missing,late\r\n");
        for (Row r : g.rows()) {
            sb.append(csv(r.name())).append(',').append(csv(r.email()));
            for (Cell c : r.cells()) sb.append(',').append(c.score() == null ? "" : c.score().toPlainString()).append(',').append(c.status() + (c.late() ? " (late)" : ""));
            sb.append(',').append(r.totalScore().toPlainString()).append(',').append(r.totalMax().toPlainString()).append(',').append(r.percent() == null ? "" : r.percent())
                    .append(',').append(r.missing()).append(',').append(r.late()).append("\r\n");
        }
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv; charset=utf-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("gradebook-course-" + courseId + ".csv").build().toString())
                .header("X-Content-Type-Options", "nosniff").body(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** Neutralises spreadsheet formula injection and quotes per RFC 4180 (student names are user-controlled). */
    public static String csv(String v) {
        if (v == null) v = "";
        if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0) v = "'" + v;
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }
}
