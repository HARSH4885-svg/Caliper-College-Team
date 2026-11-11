package dev.codesentinel.search;

import dev.codesentinel.auth.CurrentUser;
import dev.codesentinel.auth.Principal;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * Jump-to search behind the command palette. It only ever returns things the caller can already open: an instructor's own
 * courses, assignments and enrolled students; a student's enrolled courses and published assignments.
 */
@RestController
@RequestMapping("/api")
public class SearchController {
    public record Hit(String type, long id, String title, String subtitle, String path) {
    }

    private final JdbcClient jdbc;

    public SearchController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Escapes LIKE wildcards so a query of "%" or "_" matches literally. */
    static String pattern(String q) {
        return "%" + q.trim().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    @GetMapping("/search")
    List<Hit> search(@RequestParam(defaultValue = "") String q) {
        Principal p = CurrentUser.get();
        String s = q.trim();
        if (s.length() < 2 || s.length() > 80) return List.of();
        String like = pattern(s);
        List<Hit> out = new ArrayList<>();
        if (p.instructor()) {
            out.addAll(jdbc.sql("SELECT id, code, title FROM courses WHERE teaches(id, ?) AND (lower(code) LIKE ? OR lower(title) LIKE ?) ORDER BY archived, code LIMIT 5")
                    .params(p.id(), like, like).query((rs, n) -> new Hit("course", rs.getLong(1), rs.getString(2) + " · " + rs.getString(3), "Course", "/courses/" + rs.getLong(1))).list());
            out.addAll(jdbc.sql("""
                    SELECT a.id, a.title, c.code FROM assignments a JOIN courses c ON c.id=a.course_id
                    WHERE teaches(c.id, ?) AND lower(a.title) LIKE ? ORDER BY a.archived, a.deadline DESC LIMIT 6""")
                    .params(p.id(), like).query((rs, n) -> new Hit("assignment", rs.getLong(1), rs.getString(2), "Assignment · " + rs.getString(3), "/assignments/" + rs.getLong(1))).list());
            out.addAll(jdbc.sql("""
                    SELECT DISTINCT ON (u.id) u.id, u.display_name, c.code, c.id FROM users u JOIN enrollments e ON e.student_id=u.id JOIN courses c ON c.id=e.course_id
                    WHERE teaches(c.id, ?) AND (lower(u.display_name) LIKE ? OR lower(u.email) LIKE ?) ORDER BY u.id LIMIT 5""")
                    .params(p.id(), like, like).query((rs, n) -> new Hit("student", rs.getLong(1), rs.getString(2), "Student · " + rs.getString(3), "/courses/" + rs.getLong(4) + "?tab=people")).list());
        } else {
            out.addAll(jdbc.sql("""
                    SELECT c.id, c.code, c.title FROM courses c JOIN enrollments e ON e.course_id=c.id
                    WHERE e.student_id=? AND NOT c.archived AND (lower(c.code) LIKE ? OR lower(c.title) LIKE ?) ORDER BY c.code LIMIT 5""")
                    .params(p.id(), like, like).query((rs, n) -> new Hit("course", rs.getLong(1), rs.getString(2) + " · " + rs.getString(3), "Class", "/courses/" + rs.getLong(1))).list());
            out.addAll(jdbc.sql("""
                    SELECT a.id, a.title, c.code FROM assignments a JOIN courses c ON c.id=a.course_id JOIN enrollments e ON e.course_id=c.id
                    WHERE e.student_id=? AND a.published AND NOT a.archived AND NOT c.archived AND lower(a.title) LIKE ? ORDER BY a.deadline DESC LIMIT 8""")
                    .params(p.id(), like).query((rs, n) -> new Hit("assignment", rs.getLong(1), rs.getString(2), "Assignment · " + rs.getString(3), "/assignments/" + rs.getLong(1))).list());
        }
        return out;
    }
}
