package dev.codesentinel.community;

import dev.codesentinel.auth.CurrentUser;
import dev.codesentinel.auth.Principal;
import dev.codesentinel.common.Access;
import dev.codesentinel.common.ApiException;
import dev.codesentinel.common.AuditService;
import dev.codesentinel.notify.NotificationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Course Q&A. Members start threads and reply; staff answers are marked as such, staff can endorse any reply as a good answer,
 * pin and lock threads, and hide posts. Anyone can report a post; staff see the reports. Hidden posts stay in the database.
 */
@RestController
@RequestMapping("/api")
public class DiscussionController {
    public record ThreadRow(long id, String title, String author, boolean authorIsStaff, boolean mine, int replies, boolean answered, boolean pinned, boolean locked, boolean hidden, Instant createdAt, Instant lastActivity) {
    }

    public record Post(long id, String author, boolean staff, boolean mine, String body, boolean endorsed, boolean hidden, Instant createdAt) {
    }

    public record ThreadView(long id, long courseId, String title, String body, String author, boolean authorIsStaff, boolean mine, boolean pinned, boolean locked, boolean hidden, Instant createdAt, List<Post> posts, boolean canModerate) {
    }

    record NewThread(@NotBlank @Size(max = 150) String title, @NotBlank @Size(max = 10000) String body) {
    }

    record NewPost(@NotBlank @Size(max = 10000) String body) {
    }

    record Report(Long postId, @NotBlank @Size(max = 300) String reason) {
    }

    private final JdbcClient jdbc;
    private final Access access;
    private final AuditService audit;
    private final NotificationService notes;

    public DiscussionController(JdbcClient jdbc, Access access, AuditService audit, NotificationService notes) {
        this.jdbc = jdbc;
        this.access = access;
        this.audit = audit;
        this.notes = notes;
    }

    @GetMapping("/courses/{courseId}/threads")
    List<ThreadRow> list(@PathVariable long courseId, @RequestParam(defaultValue = "all") String filter, @RequestParam(required = false) String q) {
        Principal p = CurrentUser.get();
        access.requireCourseMember(courseId, p);
        boolean staff = access.ownsCourse(courseId, p);
        String like = q == null || q.isBlank() ? null : "%" + q.trim().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        return jdbc.sql("""
                SELECT t.id, t.title, u.display_name, teaches(t.course_id, t.author_id), t.author_id=?,
                  (SELECT count(*) FROM discussion_posts dp WHERE dp.thread_id=t.id AND (? OR NOT dp.hidden)),
                  EXISTS (SELECT 1 FROM discussion_posts dp WHERE dp.thread_id=t.id AND NOT dp.hidden AND (dp.endorsed OR teaches(t.course_id, dp.author_id))),
                  t.pinned, t.locked, t.hidden, t.created_at, t.last_activity
                FROM discussion_threads t JOIN users u ON u.id=t.author_id
                WHERE t.course_id=? AND (? OR NOT t.hidden OR t.author_id=?) AND (?::text IS NULL OR lower(t.title) LIKE ? OR lower(t.body) LIKE ?)
                ORDER BY t.pinned DESC, t.last_activity DESC LIMIT 200""")
                .params(p.id(), staff, courseId, staff, p.id(), like, like, like)
                .query((rs, n) -> new ThreadRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getBoolean(4), rs.getBoolean(5), rs.getInt(6), rs.getBoolean(7), rs.getBoolean(8),
                        rs.getBoolean(9), rs.getBoolean(10), rs.getTimestamp(11).toInstant(), rs.getTimestamp(12).toInstant())).list()
                .stream().filter(t -> switch (filter) {
                    case "unanswered" -> !t.answered();
                    case "mine" -> t.mine();
                    default -> true;
                }).toList();
    }

    @PostMapping("/courses/{courseId}/threads")
    ThreadView create(@PathVariable long courseId, @Valid @RequestBody NewThread r) {
        Principal p = CurrentUser.get();
        access.requireCourseMember(courseId, p);
        long id = jdbc.sql("INSERT INTO discussion_threads(course_id, author_id, title, body) VALUES (?,?,?,?) RETURNING id")
                .params(courseId, p.id(), r.title().trim(), r.body().trim()).query(Long.class).single();
        audit.log(p.id(), "THREAD_CREATE", "thread", id, null);
        if (!p.instructor()) notes.notifyCourseStaff(courseId, p.id(), "DISCUSSION", "New question: " + r.title().trim(), p.displayName(), "/threads/" + id, "thread-" + id);
        return get(id);
    }

    private long courseOf(long threadId) {
        return jdbc.sql("SELECT course_id FROM discussion_threads WHERE id=?").param(threadId).query(Long.class).optional().orElseThrow(ApiException::notFound);
    }

    @GetMapping("/threads/{id}")
    ThreadView get(@PathVariable long id) {
        Principal p = CurrentUser.get();
        long c = courseOf(id);
        access.requireCourseMember(c, p);
        boolean staff = access.ownsCourse(c, p);
        var t = jdbc.sql("SELECT t.title, t.body, u.display_name, teaches(t.course_id, t.author_id), t.author_id, t.pinned, t.locked, t.hidden, t.created_at FROM discussion_threads t JOIN users u ON u.id=t.author_id WHERE t.id=?")
                .param(id).query((rs, n) -> new Object[]{rs.getString(1), rs.getString(2), rs.getString(3), rs.getBoolean(4), rs.getLong(5), rs.getBoolean(6), rs.getBoolean(7), rs.getBoolean(8), rs.getTimestamp(9).toInstant()}).single();
        if ((Boolean) t[7] && !staff && (Long) t[4] != p.id()) throw ApiException.notFound();
        List<Post> posts = jdbc.sql("""
                SELECT dp.id, u.display_name, teaches(?, dp.author_id), dp.author_id=?, dp.body, dp.endorsed, dp.hidden, dp.created_at
                FROM discussion_posts dp JOIN users u ON u.id=dp.author_id WHERE dp.thread_id=? ORDER BY dp.created_at, dp.id""").params(c, p.id(), id)
                .query((rs, n) -> {
                    boolean hidden = rs.getBoolean(7);
                    return new Post(rs.getLong(1), rs.getString(2), rs.getBoolean(3), rs.getBoolean(4), hidden && !staff ? "" : rs.getString(5), rs.getBoolean(6), hidden, rs.getTimestamp(8).toInstant());
                }).list();
        return new ThreadView(id, c, (String) t[0], (String) t[1], (String) t[2], (Boolean) t[3], (Long) t[4] == p.id(), (Boolean) t[5], (Boolean) t[6], (Boolean) t[7], (Instant) t[8], posts, staff);
    }

    @PostMapping("/threads/{id}/posts")
    ThreadView reply(@PathVariable long id, @Valid @RequestBody NewPost r) {
        Principal p = CurrentUser.get();
        long c = courseOf(id);
        access.requireCourseMember(c, p);
        boolean staff = access.ownsCourse(c, p);
        var t = jdbc.sql("SELECT locked, hidden, author_id, title FROM discussion_threads WHERE id=?").param(id).query((rs, n) -> new Object[]{rs.getBoolean(1), rs.getBoolean(2), rs.getLong(3), rs.getString(4)}).single();
        if ((Boolean) t[0] && !staff) throw ApiException.conflict("This thread is locked");
        if ((Boolean) t[1] && !staff) throw ApiException.notFound();
        jdbc.sql("INSERT INTO discussion_posts(thread_id, author_id, body) VALUES (?,?,?)").params(id, p.id(), r.body().trim()).update();
        jdbc.sql("UPDATE discussion_threads SET last_activity=now() WHERE id=?").param(id).update();
        List<Long> watchers = jdbc.sql("SELECT author_id FROM discussion_threads WHERE id=? UNION SELECT author_id FROM discussion_posts WHERE thread_id=?").params(id, id).query(Long.class).list();
        notes.notifyUsers(watchers.stream().filter(u -> u != p.id()).toList(), "DISCUSSION", (staff ? "Your instructor replied: " : "New reply: ") + t[3], p.displayName(), "/threads/" + id, null);
        return get(id);
    }

    @PostMapping("/threads/{id}/moderate")
    ThreadView moderate(@PathVariable long id, @RequestBody Map<String, Boolean> body) {
        Principal p = CurrentUser.instructor();
        access.requireCourseOwner(courseOf(id), p);
        for (String k : List.of("pinned", "locked", "hidden")) if (body.get(k) != null) jdbc.sql("UPDATE discussion_threads SET " + k + "=? WHERE id=?").params(body.get(k), id).update();
        audit.log(p.id(), "THREAD_MODERATE", "thread", id, body.toString());
        return get(id);
    }

    private long threadOfPost(long postId) {
        return jdbc.sql("SELECT thread_id FROM discussion_posts WHERE id=?").param(postId).query(Long.class).optional().orElseThrow(ApiException::notFound);
    }

    @PostMapping("/posts/{id}/endorse")
    ThreadView endorse(@PathVariable long id, @RequestParam(defaultValue = "true") boolean on) {
        Principal p = CurrentUser.instructor();
        long t = threadOfPost(id);
        access.requireCourseOwner(courseOf(t), p);
        jdbc.sql("UPDATE discussion_posts SET endorsed=? WHERE id=?").params(on, id).update();
        return get(t);
    }

    /** Staff hide any post; authors may withdraw their own. */
    @PostMapping("/posts/{id}/hide")
    ThreadView hide(@PathVariable long id, @RequestParam(defaultValue = "true") boolean on) {
        Principal p = CurrentUser.get();
        long t = threadOfPost(id);
        long c = courseOf(t);
        access.requireCourseMember(c, p);
        long author = jdbc.sql("SELECT author_id FROM discussion_posts WHERE id=?").param(id).query(Long.class).single();
        if (!access.ownsCourse(c, p) && (author != p.id() || !on)) throw ApiException.forbidden();
        jdbc.sql("UPDATE discussion_posts SET hidden=? WHERE id=?").params(on, id).update();
        audit.log(p.id(), on ? "POST_HIDE" : "POST_UNHIDE", "post", id, null);
        return get(t);
    }

    @PostMapping("/threads/{id}/report")
    ResponseEntity<Void> report(@PathVariable long id, @Valid @RequestBody Report r) {
        Principal p = CurrentUser.get();
        long c = courseOf(id);
        access.requireCourseMember(c, p);
        if (r.postId() != null && threadOfPost(r.postId()) != id) throw ApiException.bad("That post is not in this thread");
        jdbc.sql("INSERT INTO discussion_reports(thread_id, post_id, reporter_id, reason) VALUES (?,?,?,?)").params(id, r.postId(), p.id(), r.reason().trim()).update();
        notes.notifyCourseStaff(c, p.id(), "DISCUSSION", "A post was reported", r.reason().trim(), "/threads/" + id, null);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/courses/{courseId}/reports")
    List<Map<String, Object>> reports(@PathVariable long courseId) {
        Principal p = CurrentUser.instructor();
        access.requireCourseOwner(courseId, p);
        return jdbc.sql("""
                SELECT r.id, r.thread_id, t.title, r.post_id, r.reason, u.display_name, r.created_at FROM discussion_reports r JOIN discussion_threads t ON t.id=r.thread_id
                JOIN users u ON u.id=r.reporter_id WHERE t.course_id=? AND NOT r.resolved ORDER BY r.created_at DESC""").param(courseId)
                .query((rs, n) -> Map.<String, Object>of("id", rs.getLong(1), "threadId", rs.getLong(2), "thread", rs.getString(3), "postId", rs.getLong(4), "reason", rs.getString(5),
                        "reporter", rs.getString(6), "at", rs.getTimestamp(7).toInstant())).list();
    }

    @PostMapping("/reports/{id}/resolve")
    ResponseEntity<Void> resolve(@PathVariable long id) {
        Principal p = CurrentUser.instructor();
        long t = jdbc.sql("SELECT thread_id FROM discussion_reports WHERE id=?").param(id).query(Long.class).optional().orElseThrow(ApiException::notFound);
        access.requireCourseOwner(courseOf(t), p);
        jdbc.sql("UPDATE discussion_reports SET resolved=TRUE WHERE id=?").param(id).update();
        return ResponseEntity.noContent().build();
    }
}
