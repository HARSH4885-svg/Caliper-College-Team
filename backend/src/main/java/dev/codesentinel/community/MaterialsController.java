package dev.codesentinel.community;

import dev.codesentinel.auth.CurrentUser;
import dev.codesentinel.auth.Principal;
import dev.codesentinel.common.Access;
import dev.codesentinel.common.ApiException;
import dev.codesentinel.common.AuditService;
import dev.codesentinel.storage.FileStorageProvider;
import dev.codesentinel.storage.StoreRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Course resources organised into modules: links, short notes and files (slides, PDFs, datasets). Staff edit; members read. */
@RestController
@RequestMapping("/api")
public class MaterialsController {
    public record Material(long id, String module, String title, String kind, String url, String body, String fileName, Long sizeBytes, int ord, String author, Instant createdAt) {
    }

    record MaterialInput(@Size(max = 100) String module, @NotBlank @Size(max = 150) String title, String kind, @Size(max = 1000) String url, @Size(max = 20000) String body) {
    }

    static final long MAX_FILE = 10L << 20;
    static final Map<String, String> TYPES = Map.ofEntries(Map.entry("pdf", "application/pdf"), Map.entry("png", "image/png"), Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"), Map.entry("gif", "image/gif"), Map.entry("webp", "image/webp"), Map.entry("txt", "text/plain"), Map.entry("md", "text/plain"),
            Map.entry("csv", "text/plain"), Map.entry("py", "text/plain"), Map.entry("java", "text/plain"), Map.entry("c", "text/plain"), Map.entry("cpp", "text/plain"),
            Map.entry("js", "text/plain"), Map.entry("sql", "text/plain"), Map.entry("json", "text/plain"), Map.entry("ipynb", "application/octet-stream"),
            Map.entry("docx", "application/octet-stream"), Map.entry("pptx", "application/octet-stream"), Map.entry("xlsx", "application/octet-stream"), Map.entry("zip", "application/octet-stream"));

    private final JdbcClient jdbc;
    private final Access access;
    private final AuditService audit;
    private final FileStorageProvider storage;

    public MaterialsController(JdbcClient jdbc, Access access, AuditService audit, FileStorageProvider storage) {
        this.jdbc = jdbc;
        this.access = access;
        this.audit = audit;
        this.storage = storage;
    }

    private static final String SELECT = "SELECT m.id, m.module, m.title, m.kind, m.url, m.body, m.file_name, m.size_bytes, m.ord, u.display_name, m.created_at FROM course_materials m JOIN users u ON u.id=m.author_id ";

    private static Material map(java.sql.ResultSet rs) throws java.sql.SQLException {
        long size = rs.getLong(8);
        return new Material(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.wasNull() ? null : size,
                rs.getInt(9), rs.getString(10), rs.getTimestamp(11).toInstant());
    }

    @GetMapping("/courses/{courseId}/materials")
    List<Material> list(@PathVariable long courseId) {
        access.requireCourseMember(courseId, CurrentUser.get());
        return jdbc.sql(SELECT + "WHERE m.course_id=? ORDER BY m.module, m.ord, m.id").param(courseId).query((rs, n) -> map(rs)).list();
    }

    private long courseOf(long id) {
        return jdbc.sql("SELECT course_id FROM course_materials WHERE id=?").param(id).query(Long.class).optional().orElseThrow(ApiException::notFound);
    }

    private static void check(MaterialInput r) {
        if (r.kind() == null || !List.of("LINK", "NOTE").contains(r.kind())) throw ApiException.bad("kind must be LINK or NOTE (upload files separately)");
        if (r.kind().equals("LINK") && (r.url() == null || !r.url().trim().matches("(?i)https?://\\S+"))) throw ApiException.bad("A link must start with http:// or https://");
        if (r.kind().equals("NOTE") && (r.body() == null || r.body().isBlank())) throw ApiException.bad("Write the note");
    }

    private int nextOrd(long courseId, String module) {
        return jdbc.sql("SELECT coalesce(max(ord), -1) + 1 FROM course_materials WHERE course_id=? AND module=?").params(courseId, module).query(Integer.class).single();
    }

    @PostMapping("/courses/{courseId}/materials")
    Material create(@PathVariable long courseId, @Valid @RequestBody MaterialInput r) {
        Principal p = CurrentUser.instructor();
        access.requireCourseOwner(courseId, p);
        check(r);
        String module = r.module() == null ? "" : r.module().trim();
        long id = jdbc.sql("INSERT INTO course_materials(course_id, author_id, module, title, kind, url, body, ord) VALUES (?,?,?,?,?,?,?,?) RETURNING id")
                .params(courseId, p.id(), module, r.title().trim(), r.kind(), r.kind().equals("LINK") ? r.url().trim() : null, r.body() == null ? "" : r.body(), nextOrd(courseId, module)).query(Long.class).single();
        audit.log(p.id(), "MATERIAL_ADD", "course", courseId, r.kind());
        return one(id);
    }

    @PostMapping(value = "/courses/{courseId}/materials/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    Material upload(@PathVariable long courseId, @RequestParam(defaultValue = "") String module, @RequestParam(required = false) String title, @RequestParam("file") MultipartFile file) throws IOException {
        Principal p = CurrentUser.instructor();
        access.requireCourseOwner(courseId, p);
        String name = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename().replaceAll("[^A-Za-z0-9._()+ -]", "_").trim();
        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
        if (!TYPES.containsKey(ext)) throw ApiException.bad("This file type is not accepted. Use PDF, images, text, Office documents, notebooks or .zip");
        if (file.isEmpty() || file.getSize() > MAX_FILE) throw ApiException.bad("Files must be between 1 byte and 10 MiB");
        if (name.length() > 150) name = name.substring(name.length() - 150);
        String key = "material/" + courseId + "/" + UUID.randomUUID();
        var o = storage.store(new StoreRequest(key, file.getInputStream()));
        String t = title == null || title.isBlank() ? name : title.trim();
        if (t.length() > 150) t = t.substring(0, 150);
        String m = module.trim().length() > 100 ? module.trim().substring(0, 100) : module.trim();
        long id = jdbc.sql("INSERT INTO course_materials(course_id, author_id, module, title, kind, storage_key, file_name, size_bytes, ord) VALUES (?,?,?,?,'FILE',?,?,?,?) RETURNING id")
                .params(courseId, p.id(), m, t, key, name, o.sizeBytes(), nextOrd(courseId, m)).query(Long.class).single();
        audit.log(p.id(), "MATERIAL_ADD", "course", courseId, "FILE " + name);
        return one(id);
    }

    @PatchMapping("/materials/{id}")
    Material patch(@PathVariable long id, @RequestBody Map<String, Object> body) {
        Principal p = CurrentUser.instructor();
        access.requireCourseOwner(courseOf(id), p);
        if (body.get("title") instanceof String t && !t.isBlank() && t.length() <= 150) jdbc.sql("UPDATE course_materials SET title=? WHERE id=?").params(t.trim(), id).update();
        if (body.get("module") instanceof String m && m.length() <= 100) jdbc.sql("UPDATE course_materials SET module=? WHERE id=?").params(m.trim(), id).update();
        if (body.get("body") instanceof String b && b.length() <= 20000) jdbc.sql("UPDATE course_materials SET body=? WHERE id=?").params(b, id).update();
        if (body.get("move") instanceof String dir && (dir.equals("up") || dir.equals("down"))) {
            var me = jdbc.sql("SELECT course_id, module, ord FROM course_materials WHERE id=?").param(id).query((rs, n) -> new Object[]{rs.getLong(1), rs.getString(2), rs.getInt(3)}).single();
            var other = jdbc.sql("SELECT id, ord FROM course_materials WHERE course_id=? AND module=? AND ord " + (dir.equals("up") ? "<" : ">") + " ? ORDER BY ord " + (dir.equals("up") ? "DESC" : "ASC") + " LIMIT 1")
                    .params(me[0], me[1], me[2]).query((rs, n) -> new long[]{rs.getLong(1), rs.getInt(2)}).optional();
            other.ifPresent(o -> {
                jdbc.sql("UPDATE course_materials SET ord=? WHERE id=?").params((int) o[1], id).update();
                jdbc.sql("UPDATE course_materials SET ord=? WHERE id=?").params(me[2], o[0]).update();
            });
        }
        return one(id);
    }

    @DeleteMapping("/materials/{id}")
    ResponseEntity<Void> delete(@PathVariable long id) {
        Principal p = CurrentUser.instructor();
        long c = courseOf(id);
        access.requireCourseOwner(c, p);
        jdbc.sql("DELETE FROM course_materials WHERE id=?").param(id).update();
        audit.log(p.id(), "MATERIAL_DELETE", "course", c, "material " + id);
        return ResponseEntity.noContent().build();
    }

    /** Safe types (PDF, images, plain text) carry their real content type so the browser can preview them; everything else downloads. */
    @GetMapping("/materials/{id}/file")
    ResponseEntity<InputStreamResource> file(@PathVariable long id) {
        access.requireCourseMember(courseOf(id), CurrentUser.get());
        var row = jdbc.sql("SELECT storage_key, file_name FROM course_materials WHERE id=? AND kind='FILE'").param(id).query((rs, n) -> new String[]{rs.getString(1), rs.getString(2)}).optional().orElseThrow(ApiException::notFound);
        String ext = row[1].contains(".") ? row[1].substring(row[1].lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(TYPES.getOrDefault(ext, "application/octet-stream")))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(row[1]).build().toString())
                .header("X-Content-Type-Options", "nosniff").body(new InputStreamResource(storage.read(row[0])));
    }

    private Material one(long id) {
        return jdbc.sql(SELECT + "WHERE m.id=?").param(id).query((rs, n) -> map(rs)).single();
    }
}
