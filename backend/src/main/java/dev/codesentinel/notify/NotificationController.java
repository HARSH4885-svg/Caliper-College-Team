package dev.codesentinel.notify;

import dev.codesentinel.auth.CurrentUser;
import dev.codesentinel.auth.Principal;
import dev.codesentinel.common.ApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    public record Note(long id, String kind, String title, String body, String link, Instant createdAt, boolean read) {
    }

    private final JdbcClient jdbc;
    private final NotificationService notes;

    public NotificationController(JdbcClient jdbc, NotificationService notes) {
        this.jdbc = jdbc;
        this.notes = notes;
    }

    @GetMapping
    Map<String, Object> list(@RequestParam(defaultValue = "false") boolean unread, @RequestParam(defaultValue = "50") int limit) {
        Principal p = CurrentUser.get();
        if (!p.instructor()) notes.deadlineReminders(p.id());
        List<Note> items = jdbc.sql("SELECT id, kind, title, body, link, created_at, read_at IS NOT NULL FROM notifications WHERE user_id=? AND (NOT ? OR read_at IS NULL) ORDER BY created_at DESC, id DESC LIMIT ?")
                .params(p.id(), unread, Math.max(1, Math.min(200, limit))).query((rs, n) -> new Note(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getTimestamp(6).toInstant(), rs.getBoolean(7))).list();
        long count = jdbc.sql("SELECT count(*) FROM notifications WHERE user_id=? AND read_at IS NULL").param(p.id()).query(Long.class).single();
        return Map.of("unread", count, "items", items);
    }

    @PostMapping("/{id}/read")
    ResponseEntity<Void> read(@PathVariable long id) {
        Principal p = CurrentUser.get();
        if (jdbc.sql("UPDATE notifications SET read_at=coalesce(read_at, now()) WHERE id=? AND user_id=?").params(id, p.id()).update() == 0) throw ApiException.notFound();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    Map<String, Integer> readAll() {
        Principal p = CurrentUser.get();
        return Map.of("read", jdbc.sql("UPDATE notifications SET read_at=now() WHERE user_id=? AND read_at IS NULL").param(p.id()).update());
    }

    @GetMapping("/preferences")
    Map<String, Boolean> prefs() {
        Principal p = CurrentUser.get();
        Set<String> muted = new HashSet<>(jdbc.sql("SELECT kind FROM notification_mutes WHERE user_id=?").param(p.id()).query(String.class).list());
        Map<String, Boolean> m = new TreeMap<>();
        for (String k : NotificationService.KINDS) m.put(k, !muted.contains(k));
        return m;
    }

    @PutMapping("/preferences")
    Map<String, Boolean> setPrefs(@RequestBody Map<String, Boolean> body) {
        Principal p = CurrentUser.get();
        for (var e : body.entrySet()) {
            if (!NotificationService.KINDS.contains(e.getKey())) throw ApiException.bad("Unknown notification kind " + e.getKey());
            if (Boolean.FALSE.equals(e.getValue())) jdbc.sql("INSERT INTO notification_mutes(user_id, kind) VALUES (?,?) ON CONFLICT DO NOTHING").params(p.id(), e.getKey()).update();
            else jdbc.sql("DELETE FROM notification_mutes WHERE user_id=? AND kind=?").params(p.id(), e.getKey()).update();
        }
        return prefs();
    }
}
