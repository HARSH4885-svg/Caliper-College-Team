package dev.codesentinel.community;

import dev.codesentinel.auth.CurrentUser;
import dev.codesentinel.auth.Principal;
import dev.codesentinel.common.ApiException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Everything with a date in the caller's courses: deadlines, when work opens, late cut-offs, and course start and end.
 * Students only see published, visible work and whether they have handed it in.
 */
@RestController
@RequestMapping("/api")
public class CalendarController {
    public record Event(String kind, Instant at, String title, long courseId, String courseCode, Long assignmentId, String assignmentKind, Boolean done, boolean draft) {
    }

    private final JdbcClient jdbc;

    public CalendarController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/calendar")
    List<Event> events(@RequestParam Instant from, @RequestParam Instant to) {
        Principal p = CurrentUser.get();
        if (!to.isAfter(from) || ChronoUnit.DAYS.between(from, to) > 120) throw ApiException.bad("Ask for at most 120 days at a time");
        boolean inst = p.instructor();
        String courses = inst ? "SELECT id FROM courses c WHERE teaches(c.id, ?) AND NOT c.archived"
                : "SELECT c.id FROM courses c JOIN enrollments e ON e.course_id=c.id WHERE e.student_id=? AND NOT c.archived";
        String visible = inst ? "NOT a.archived" : "a.published AND NOT a.archived AND (a.available_from IS NULL OR a.available_from <= now())";
        String done = "(EXISTS (SELECT 1 FROM submissions s WHERE s.assignment_id=a.id AND s.student_id=?) OR EXISTS (SELECT 1 FROM quiz_attempts q WHERE q.assignment_id=a.id AND q.student_id=? AND q.status<>'IN_PROGRESS'))";
        List<Event> out = new ArrayList<>(jdbc.sql("SELECT 'DUE', a.deadline, a.title, c.id, c.code, a.id, a.kind, " + done + ", NOT a.published FROM assignments a JOIN courses c ON c.id=a.course_id "
                        + "WHERE c.id IN (" + courses + ") AND " + visible + " AND a.deadline BETWEEN ? AND ?")
                .params(p.id(), p.id(), p.id(), java.sql.Timestamp.from(from), java.sql.Timestamp.from(to))
                .query((rs, n) -> new Event(rs.getString(1), rs.getTimestamp(2).toInstant(), rs.getString(3), rs.getLong(4), rs.getString(5), rs.getLong(6), rs.getString(7), inst ? null : rs.getBoolean(8), rs.getBoolean(9))).list());
        // when work opens (instructors see scheduled releases; students see what opened in the window)
        out.addAll(jdbc.sql("SELECT a.available_from, a.title, c.id, c.code, a.id, a.kind, NOT a.published FROM assignments a JOIN courses c ON c.id=a.course_id WHERE c.id IN (" + courses + ") AND "
                        + (inst ? "NOT a.archived" : "a.published AND NOT a.archived AND a.available_from <= now()") + " AND a.available_from BETWEEN ? AND ?")
                .params(p.id(), java.sql.Timestamp.from(from), java.sql.Timestamp.from(to))
                .query((rs, n) -> new Event("OPENS", rs.getTimestamp(1).toInstant(), rs.getString(2), rs.getLong(3), rs.getString(4), rs.getLong(5), rs.getString(6), null, rs.getBoolean(7))).list());
        out.addAll(jdbc.sql("SELECT a.late_until, a.title, c.id, c.code, a.id, a.kind FROM assignments a JOIN courses c ON c.id=a.course_id WHERE c.id IN (" + courses + ") AND " + visible
                        + " AND a.late_policy<>'BLOCK' AND a.late_until BETWEEN ? AND ?")
                .params(p.id(), java.sql.Timestamp.from(from), java.sql.Timestamp.from(to))
                .query((rs, n) -> new Event("LATE_CLOSES", rs.getTimestamp(1).toInstant(), rs.getString(2), rs.getLong(3), rs.getString(4), rs.getLong(5), rs.getString(6), null, false)).list());
        out.addAll(jdbc.sql("SELECT 'COURSE_START', starts_on, title, id, code FROM courses WHERE id IN (" + courses + ") AND starts_on IS NOT NULL AND starts_on BETWEEN ?::date AND ?::date "
                        + "UNION ALL SELECT 'COURSE_END', ends_on, title, id, code FROM courses WHERE id IN (" + courses + ") AND ends_on IS NOT NULL AND ends_on BETWEEN ?::date AND ?::date")
                .params(p.id(), java.sql.Timestamp.from(from), java.sql.Timestamp.from(to), p.id(), java.sql.Timestamp.from(from), java.sql.Timestamp.from(to))
                .query((rs, n) -> new Event(rs.getString(1), rs.getDate(2).toLocalDate().atStartOfDay(java.time.ZoneOffset.UTC).toInstant(), rs.getString(3), rs.getLong(4), rs.getString(5), null, null, null, false)).list());
        out.sort(Comparator.comparing(Event::at));
        return out;
    }
}
