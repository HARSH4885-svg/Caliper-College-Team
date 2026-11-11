package dev.codesentinel.notify;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * In-app notifications. Each kind can be muted per user. A {@code dedupe} key makes an event notify at most once per user
 * (for example re-publishing an assignment, or the "due soon" reminder). No email is sent: there is no mail service.
 */
@Service
public class NotificationService {
    public static final Set<String> KINDS = Set.of("ASSIGNMENT", "DEADLINE", "ANNOUNCEMENT", "GRADE", "DISCUSSION", "ENROLMENT");
    private final JdbcClient jdbc;

    public NotificationService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    static String cut(String s, int n) {
        return s == null ? "" : s.length() > n ? s.substring(0, n - 1) + "…" : s;
    }

    public void notifyUser(long userId, String kind, String title, String body, String link, String dedupe) {
        jdbc.sql("""
                INSERT INTO notifications(user_id, kind, title, body, link, dedupe)
                SELECT ?, ?, ?, ?, ?, ? WHERE NOT EXISTS (SELECT 1 FROM notification_mutes m WHERE m.user_id=? AND m.kind=?)
                ON CONFLICT DO NOTHING""").params(userId, kind, cut(title, 200), cut(body, 500), cut(link, 300), dedupe, userId, kind).update();
    }

    public void notifyUsers(List<Long> users, String kind, String title, String body, String link, String dedupe) {
        for (long u : users) notifyUser(u, kind, title, body, link, dedupe);
    }

    public void notifyCourseStudents(long courseId, String kind, String title, String body, String link, String dedupe) {
        notifyUsers(jdbc.sql("SELECT student_id FROM enrollments WHERE course_id=?").param(courseId).query(Long.class).list(), kind, title, body, link, dedupe);
    }

    public void notifyCourseStaff(long courseId, long except, String kind, String title, String body, String link, String dedupe) {
        List<Long> staff = jdbc.sql("SELECT instructor_id FROM courses WHERE id=? UNION SELECT user_id FROM course_staff WHERE course_id=?").params(courseId, courseId).query(Long.class).list();
        notifyUsers(staff.stream().filter(x -> x != except).toList(), kind, title, body, link, dedupe);
    }

    /** "Due soon" reminders, created when a student looks at their notifications: work due in the next 48 hours that they have not handed in. */
    public void deadlineReminders(long studentId) {
        var due = jdbc.sql("""
                SELECT a.id, a.title, c.code, a.deadline FROM assignments a JOIN courses c ON c.id=a.course_id JOIN enrollments e ON e.course_id=c.id AND e.student_id=?
                WHERE a.published AND NOT a.archived AND NOT c.archived AND (a.available_from IS NULL OR a.available_from <= now())
                  AND a.deadline > now() AND a.deadline < now() + interval '48 hours'
                  AND NOT EXISTS (SELECT 1 FROM submissions s WHERE s.assignment_id=a.id AND s.student_id=e.student_id)
                  AND NOT EXISTS (SELECT 1 FROM quiz_attempts q WHERE q.assignment_id=a.id AND q.student_id=e.student_id AND q.status<>'IN_PROGRESS')""").param(studentId)
                .query((rs, n) -> new Object[]{rs.getLong(1), rs.getString(2), rs.getString(3), rs.getTimestamp(4).toInstant()}).list();
        for (Object[] d : due)
            notifyUser(studentId, "DEADLINE", d[1] + " is due soon", d[2] + " · due " + d[3], "/assignments/" + d[0], "due-" + d[0]);
    }
}
