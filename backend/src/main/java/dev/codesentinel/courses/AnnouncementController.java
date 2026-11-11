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
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

/** A per-course stream. Instructors of the course post, pin and remove; enrolled students read. */
@RestController
@RequestMapping("/api")
public class AnnouncementController {
    public record AnnouncementDto(long id, long courseId, String title, String body, boolean pinned, String author, Instant createdAt) {
    }

    record CreateAnnouncement(@NotBlank @Size(max = 150) String title, @NotBlank @Size(max = 5000) String body, boolean pinned) {
    }

    private final JdbcClient jdbc;
    private final Access access;
    private final AuditService audit;
    private final dev.codesentinel.notify.NotificationService notes;

    public AnnouncementController(JdbcClient jdbc, Access access, AuditService audit, dev.codesentinel.notify.NotificationService notes) {
        this.notes = notes;
        this.jdbc = jdbc;
        this.access = access;
        this.audit = audit;
    }

    private static final String SELECT = "SELECT a.id, a.course_id, a.title, a.body, a.pinned, u.display_name, a.created_at FROM announcements a JOIN users u ON u.id=a.author_id ";

    private static AnnouncementDto map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new AnnouncementDto(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4), rs.getBoolean(5), rs.getString(6), rs.getTimestamp(7).toInstant());
    }

    @GetMapping("/courses/{courseId}/announcements")
    List<AnnouncementDto> list(@PathVariable long courseId) {
        Principal p = CurrentUser.get();
        access.requireCourseMember(courseId, p);
        return jdbc.sql(SELECT + "WHERE a.course_id=? ORDER BY a.pinned DESC, a.created_at DESC, a.id DESC LIMIT 100").param(courseId).query((rs, n) -> map(rs)).list();
    }

    @PostMapping("/courses/{courseId}/announcements")
    AnnouncementDto create(@PathVariable long courseId, @Valid @RequestBody CreateAnnouncement r) {
        Principal p = CurrentUser.instructor();
        access.requireCourseOwner(courseId, p);
        String title = r.title().trim(), body = r.body().trim();
        if (title.isEmpty() || body.isEmpty()) throw ApiException.bad("Title and message are required");
        long id = jdbc.sql("INSERT INTO announcements(course_id, author_id, title, body, pinned) VALUES (?,?,?,?,?) RETURNING id")
                .params(courseId, p.id(), title, body, r.pinned()).query(Long.class).single();
        audit.log(p.id(), "ANNOUNCEMENT_CREATE", "announcement", id, null);
        String code = jdbc.sql("SELECT code FROM courses WHERE id=?").param(courseId).query(String.class).single();
        notes.notifyCourseStudents(courseId, "ANNOUNCEMENT", code + ": " + title, body, "/courses/" + courseId + "?tab=news", "ann-" + id);
        return one(id);
    }

    @PostMapping("/announcements/{id}/pin")
    AnnouncementDto pin(@PathVariable long id, @RequestParam(defaultValue = "true") boolean pinned) {
        Principal p = CurrentUser.instructor();
        access.requireCourseOwner(courseOf(id), p);
        jdbc.sql("UPDATE announcements SET pinned=? WHERE id=?").params(pinned, id).update();
        audit.log(p.id(), pinned ? "ANNOUNCEMENT_PIN" : "ANNOUNCEMENT_UNPIN", "announcement", id, null);
        return one(id);
    }

    @DeleteMapping("/announcements/{id}")
    ResponseEntity<Void> delete(@PathVariable long id) {
        Principal p = CurrentUser.instructor();
        access.requireCourseOwner(courseOf(id), p);
        jdbc.sql("DELETE FROM announcements WHERE id=?").param(id).update();
        audit.log(p.id(), "ANNOUNCEMENT_DELETE", "announcement", id, null);
        return ResponseEntity.noContent().build();
    }

    private long courseOf(long id) {
        return jdbc.sql("SELECT course_id FROM announcements WHERE id=?").param(id).query(Long.class).optional().orElseThrow(ApiException::notFound);
    }

    private AnnouncementDto one(long id) {
        return jdbc.sql(SELECT + "WHERE a.id=?").param(id).query((rs, n) -> map(rs)).single();
    }
}
