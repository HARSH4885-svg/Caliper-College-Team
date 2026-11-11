package dev.codesentinel.reports;

import dev.codesentinel.auth.CurrentUser;
import dev.codesentinel.auth.Principal;
import dev.codesentinel.common.Access;
import dev.codesentinel.common.ApiException;
import dev.codesentinel.common.AuditService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

/**
 * The course home page and the course analytics tab. Every figure is computed here from submissions, attempts, enrolments and
 * announcements; the instructor view names students, the student view only ever contains the caller's own work.
 */
@RestController
@RequestMapping("/api")
public class CourseOverviewController {
    public record Upcoming(long assignmentId, String title, String kind, Instant deadline, BigDecimal maxMarks, Integer handedIn, String myStatus) {
    }

    public record Event(String kind, String who, String what, long assignmentId, String detail, Instant at) {
    }

    public record AssignmentStat(long id, String title, String kind, Instant deadline, int handedIn, int enrolled, Double averagePct, Double highestPct, Double lowestPct,
                                 int late, int needsMarking, Double medianPct) {
    }

    public record AtRisk(long studentId, String name, Double percent, int missing, int late, String reason) {
    }

    public record Overview(int students, int published, int drafts, Double averagePct, Double completionPct, int needsMarking, int lateWork,
                           List<Upcoming> upcoming, List<Event> activity, List<AtRisk> atRisk, Double myPercent, int myCompleted, int myMissing) {
    }

    public record Analytics(List<Map<String, Object>> enrollment, List<AssignmentStat> assignments, List<Map<String, Object>> weekly,
                            List<Map<String, Object>> distribution, List<Map<String, Object>> hardestQuestions, List<Map<String, Object>> languages) {
    }

    record CodingGrade(@DecimalMin("0") BigDecimal score, Boolean clearScore, @Size(max = 5000) String feedback, @Size(max = 5000) String privateNote) {
    }

    private final JdbcClient jdbc;
    private final Access access;
    private final InsightsController insights;
    private final AuditService audit;
    private final dev.codesentinel.notify.NotificationService notes;

    public CourseOverviewController(JdbcClient jdbc, Access access, InsightsController insights, AuditService audit, dev.codesentinel.notify.NotificationService notes) {
        this.notes = notes;
        this.jdbc = jdbc;
        this.access = access;
        this.insights = insights;
        this.audit = audit;
    }

    static Double pct(BigDecimal got, BigDecimal max) {
        return got == null || max == null || max.signum() == 0 ? null : Math.round(got.doubleValue() * 1000 / max.doubleValue()) / 10.0;
    }

    @GetMapping("/courses/{courseId}/overview")
    Overview overview(@PathVariable long courseId) {
        Principal p = CurrentUser.get();
        access.requireCourseMember(courseId, p);
        boolean staff = access.ownsCourse(courseId, p);
        var book = insights.book(courseId, staff ? null : p.id());
        int students = jdbc.sql("SELECT count(*) FROM enrollments WHERE course_id=?").param(courseId).query(Integer.class).single();
        int published = jdbc.sql("SELECT count(*) FROM assignments WHERE course_id=? AND published AND NOT archived").param(courseId).query(Integer.class).single();
        int drafts = staff ? jdbc.sql("SELECT count(*) FROM assignments WHERE course_id=? AND NOT published AND NOT archived").param(courseId).query(Integer.class).single() : 0;

        List<Upcoming> upcoming = jdbc.sql("""
                SELECT a.id, a.title, a.kind, a.deadline, a.max_marks,
                  (SELECT count(DISTINCT s.student_id) FROM submissions s WHERE s.assignment_id=a.id) + (SELECT count(DISTINCT q.student_id) FROM quiz_attempts q WHERE q.assignment_id=a.id AND q.status<>'IN_PROGRESS')
                FROM assignments a WHERE a.course_id=? AND a.published AND NOT a.archived AND a.deadline > now() AND (a.available_from IS NULL OR a.available_from <= now())
                ORDER BY a.deadline LIMIT 6""").param(courseId)
                .query((rs, n) -> new Upcoming(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getTimestamp(4).toInstant(), rs.getBigDecimal(5), staff ? rs.getInt(6) : null, null)).list();
        if (!staff && !book.rows().isEmpty()) {
            Map<Long, String> st = new HashMap<>();
            for (var c : book.rows().get(0).cells()) st.put(c.assignmentId(), c.status());
            upcoming = upcoming.stream().map(u -> new Upcoming(u.assignmentId(), u.title(), u.kind(), u.deadline(), u.maxMarks(), null, st.getOrDefault(u.assignmentId(), "NOT_STARTED"))).toList();
        }

        Long who = staff ? null : p.id();
        List<Event> activity = new ArrayList<>(jdbc.sql("""
                SELECT 'submission', u.display_name, a.title, a.id, coalesce(r.verdict, s.status), s.created_at FROM submissions s JOIN assignments a ON a.id=s.assignment_id JOIN users u ON u.id=s.student_id
                LEFT JOIN evaluation_results r ON r.submission_id=s.id WHERE a.course_id=? AND (?::bigint IS NULL OR s.student_id=?)
                UNION ALL
                SELECT 'quiz', u.display_name, a.title, a.id, q.status, q.submitted_at FROM quiz_attempts q JOIN assignments a ON a.id=q.assignment_id JOIN users u ON u.id=q.student_id
                WHERE a.course_id=? AND q.submitted_at IS NOT NULL AND (?::bigint IS NULL OR q.student_id=?)
                UNION ALL
                SELECT 'announcement', u.display_name, an.title, 0, '', an.created_at FROM announcements an JOIN users u ON u.id=an.author_id WHERE an.course_id=?
                ORDER BY 6 DESC LIMIT 15""").params(courseId, who, who, courseId, who, who, courseId)
                .query((rs, n) -> new Event(rs.getString(1), rs.getString(2), rs.getString(3), rs.getLong(4), rs.getString(5), rs.getTimestamp(6).toInstant())).list());
        if (staff) activity.addAll(jdbc.sql("SELECT 'enrolment', u.display_name, '', 0, '', e.created_at FROM enrollments e JOIN users u ON u.id=e.student_id WHERE e.course_id=? ORDER BY e.created_at DESC LIMIT 5")
                .param(courseId).query((rs, n) -> new Event(rs.getString(1), rs.getString(2), rs.getString(3), 0, "", rs.getTimestamp(6).toInstant())).list());
        activity.sort(Comparator.comparing(Event::at).reversed());
        if (activity.size() > 15) activity = activity.subList(0, 15);

        Double avg = null, completion = null, myPct = null;
        int myCompleted = 0, myMissing = 0, lateWork = 0;
        List<AtRisk> risk = new ArrayList<>();
        if (staff) {
            double sum = 0;
            int counted = 0, done = 0, due = 0;
            Instant now = Instant.now();
            for (var r : book.rows()) {
                if (r.percent() != null) {
                    sum += r.percent();
                    counted++;
                }
                lateWork += r.late();
                for (int i = 0; i < r.cells().size(); i++) {
                    if (book.assignments().get(i).deadline().isAfter(now)) continue;
                    due++;
                    if (!r.cells().get(i).status().equals("MISSING")) done++;
                }
                String reason = null;
                if (r.missing() >= 2) reason = r.missing() + " assignments missed";
                else if (r.percent() != null && r.percent() < 40) reason = "scoring " + r.percent() + "% overall";
                if (reason != null) risk.add(new AtRisk(r.studentId(), r.name(), r.percent(), r.missing(), r.late(), reason));
            }
            avg = counted == 0 ? null : Math.round(sum * 10 / counted) / 10.0;
            completion = due == 0 ? null : Math.round(done * 1000.0 / due) / 10.0;
            risk.sort(Comparator.comparing((AtRisk a) -> -a.missing()).thenComparing(a -> a.percent() == null ? 0 : a.percent()));
            if (risk.size() > 8) risk = risk.subList(0, 8);
        } else if (!book.rows().isEmpty()) {
            var r = book.rows().get(0);
            myPct = r.percent();
            myCompleted = r.completed();
            myMissing = r.missing();
        }
        int needsMarking = staff ? jdbc.sql("SELECT count(*) FROM quiz_attempts q JOIN assignments a ON a.id=q.assignment_id WHERE a.course_id=? AND q.status='SUBMITTED'").param(courseId).query(Integer.class).single() : 0;
        return new Overview(students, published, drafts, avg, completion, needsMarking, lateWork, upcoming, activity, risk, myPct, myCompleted, myMissing);
    }

    @GetMapping("/courses/{courseId}/analytics")
    Analytics analytics(@PathVariable long courseId) {
        Principal p = CurrentUser.instructor();
        access.requireCourseOwner(courseId, p);
        var book = insights.book(courseId, null);
        List<Map<String, Object>> enrollment = jdbc.sql("""
                SELECT d::date, (SELECT count(*) FROM enrollments e WHERE e.course_id=? AND e.created_at < d + interval '1 day')
                FROM generate_series(date_trunc('week', (SELECT coalesce(min(created_at), now()) FROM enrollments WHERE course_id=?)), now(), interval '1 week') d""")
                .params(courseId, courseId).query((rs, n) -> Map.<String, Object>of("date", rs.getObject(1, LocalDate.class).toString(), "students", rs.getInt(2))).list();
        List<AssignmentStat> stats = new ArrayList<>();
        for (int i = 0; i < book.assignments().size(); i++) {
            var a = book.assignments().get(i);
            List<Double> pcts = new ArrayList<>();
            int handed = 0, late = 0;
            for (var r : book.rows()) {
                var c = r.cells().get(i);
                if (!c.status().equals("MISSING") && !c.status().equals("NOT_STARTED")) handed++;
                if (c.late()) late++;
                Double x = pct(c.score(), a.maxMarks());
                if (x != null) pcts.add(x);
            }
            Collections.sort(pcts);
            int marking = a.kind().equals("QUIZ") ? jdbc.sql("SELECT count(*) FROM quiz_attempts WHERE assignment_id=? AND status='SUBMITTED'").param(a.id()).query(Integer.class).single() : 0;
            stats.add(new AssignmentStat(a.id(), a.title(), a.kind(), a.deadline(), handed, book.rows().size(),
                    pcts.isEmpty() ? null : Math.round(pcts.stream().mapToDouble(d -> d).average().orElse(0) * 10) / 10.0,
                    pcts.isEmpty() ? null : pcts.get(pcts.size() - 1), pcts.isEmpty() ? null : pcts.get(0), late, marking, pcts.isEmpty() ? null : pcts.get(pcts.size() / 2)));
        }
        List<Map<String, Object>> weekly = jdbc.sql("""
                SELECT w::date,
                  (SELECT count(*) FROM submissions s JOIN assignments a ON a.id=s.assignment_id WHERE a.course_id=? AND s.created_at >= w AND s.created_at < w + interval '1 week'),
                  (SELECT count(*) FROM quiz_attempts q JOIN assignments a ON a.id=q.assignment_id WHERE a.course_id=? AND q.submitted_at >= w AND q.submitted_at < w + interval '1 week')
                FROM generate_series(date_trunc('week', now()) - interval '9 weeks', date_trunc('week', now()), interval '1 week') w""")
                .params(courseId, courseId).query((rs, n) -> Map.<String, Object>of("week", rs.getObject(1, LocalDate.class).toString(), "code", rs.getInt(2), "quiz", rs.getInt(3))).list();
        int[] bins = new int[10];
        for (var r : book.rows()) if (r.percent() != null) bins[Math.min(9, (int) (r.percent() / 10))]++;
        List<Map<String, Object>> dist = new ArrayList<>();
        for (int i = 0; i < 10; i++) dist.add(Map.of("label", i * 10 + (i == 9 ? "-100%" : "-" + (i * 10 + 9) + "%"), "count", bins[i]));
        List<Map<String, Object>> hardest = jdbc.sql("""
                SELECT qu.id, a.title, left(qu.prompt, 140), qu.type, qu.marks, avg(coalesce(r.manual_score, r.auto_score) / nullif(qu.marks, 0)) * 100, count(r.*)
                FROM questions qu JOIN assignments a ON a.id=qu.assignment_id JOIN question_responses r ON r.question_id=qu.id JOIN quiz_attempts t ON t.id=r.attempt_id
                WHERE a.course_id=? AND t.status<>'IN_PROGRESS' AND coalesce(r.manual_score, r.auto_score) IS NOT NULL AND qu.marks > 0
                GROUP BY qu.id, a.title HAVING count(r.*) >= 3 ORDER BY 6 LIMIT 8""").param(courseId)
                .query((rs, n) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("questionId", rs.getLong(1));
                    m.put("assignment", rs.getString(2));
                    m.put("prompt", rs.getString(3));
                    m.put("type", rs.getString(4));
                    m.put("averagePct", Math.round(rs.getDouble(6) * 10) / 10.0);
                    m.put("answers", rs.getInt(7));
                    return m;
                }).list();
        List<Map<String, Object>> langs = jdbc.sql("""
                SELECT a.language, count(*), count(*) FILTER (WHERE r.verdict='AC'), avg(r.millis)
                FROM submissions s JOIN assignments a ON a.id=s.assignment_id JOIN evaluation_results r ON r.submission_id=s.id WHERE a.course_id=? GROUP BY a.language ORDER BY 2 DESC""")
                .param(courseId).query((rs, n) -> Map.<String, Object>of("language", rs.getString(1), "submissions", rs.getInt(2), "accepted", rs.getInt(3), "avgMillis", Math.round(rs.getDouble(4)))).list();
        return new Analytics(enrollment, stats, weekly, dist, hardest, langs);
    }

    /** Instructor marks for a coding submission: an optional override of the automatic score, feedback the student sees, a private note. */
    @PostMapping("/submissions/{id}/grade")
    Map<String, Object> gradeCoding(@PathVariable long id, @Valid @RequestBody CodingGrade g) {
        Principal p = CurrentUser.instructor();
        long aid = jdbc.sql("SELECT assignment_id FROM submissions WHERE id=?").param(id).query(Long.class).optional().orElseThrow(ApiException::notFound);
        access.requireAssignmentOwner(aid, p);
        BigDecimal max = jdbc.sql("SELECT max_marks FROM assignments WHERE id=?").param(aid).query(BigDecimal.class).single();
        if (g.score() != null && g.score().compareTo(max) > 0) throw ApiException.bad("The score cannot exceed " + max.stripTrailingZeros().toPlainString());
        if (g.score() != null) jdbc.sql("UPDATE submissions SET override_score=? WHERE id=?").params(g.score(), id).update();
        if (Boolean.TRUE.equals(g.clearScore())) jdbc.sql("UPDATE submissions SET override_score=NULL WHERE id=?").param(id).update();
        if (g.feedback() != null) jdbc.sql("UPDATE submissions SET feedback=? WHERE id=?").params(g.feedback().trim(), id).update();
        if (g.privateNote() != null) jdbc.sql("UPDATE submissions SET private_note=? WHERE id=?").params(g.privateNote().trim(), id).update();
        audit.log(p.id(), "SUBMISSION_GRADE", "submission", id, null);
        if ((g.feedback() != null && !g.feedback().isBlank()) || g.score() != null)
            jdbc.sql("SELECT s.student_id, a.title FROM submissions s JOIN assignments a ON a.id=s.assignment_id WHERE s.id=?").param(id)
                    .query((rs, n) -> { notes.notifyUser(rs.getLong(1), "GRADE", "Feedback on " + rs.getString(2), g.feedback() == null ? "" : g.feedback(), "/submissions/" + id, "sub-" + id + "-fb"); return 0; }).list();
        return grade(id);
    }

    @GetMapping("/submissions/{id}/grade")
    Map<String, Object> grade(@PathVariable long id) {
        Principal p = CurrentUser.get();
        var row = jdbc.sql("""
                SELECT s.assignment_id, s.student_id, s.override_score, s.feedback, s.private_note, s.late, a.max_marks, a.late_policy, a.late_penalty_pct, r.passed, r.total
                FROM submissions s JOIN assignments a ON a.id=s.assignment_id LEFT JOIN evaluation_results r ON r.submission_id=s.id WHERE s.id=?""").param(id)
                .query((rs, n) -> new Object[]{rs.getLong(1), rs.getLong(2), rs.getBigDecimal(3), rs.getString(4), rs.getString(5), rs.getBoolean(6), rs.getBigDecimal(7), rs.getString(8),
                        rs.getInt(9), rs.getObject(10), rs.getObject(11)}).optional().orElseThrow(ApiException::notFound);
        if (p.instructor()) access.requireAssignmentOwner((Long) row[0], p);
        else if ((Long) row[1] != p.id()) throw ApiException.notFound();
        BigDecimal max = (BigDecimal) row[6];
        BigDecimal auto = row[9] == null ? null : ((Integer) row[10]) == 0 ? BigDecimal.ZERO
                : InsightsController.penalise(max.multiply(BigDecimal.valueOf((Integer) row[9])).divide(BigDecimal.valueOf((Integer) row[10]), 2, java.math.RoundingMode.HALF_UP), (Boolean) row[5], (String) row[7], (Integer) row[8]);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("maxMarks", max);
        m.put("autoScore", auto);
        m.put("overrideScore", row[2]);
        m.put("score", row[2] != null ? row[2] : auto);
        m.put("late", row[5]);
        m.put("feedback", row[3]);
        if (p.instructor()) m.put("privateNote", row[4]);
        return m;
    }
}
