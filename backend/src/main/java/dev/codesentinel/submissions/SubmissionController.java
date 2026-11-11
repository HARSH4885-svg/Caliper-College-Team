package dev.codesentinel.submissions;

import dev.codesentinel.auth.CurrentUser;
import dev.codesentinel.auth.Principal;
import dev.codesentinel.common.Access;
import dev.codesentinel.common.ApiException;
import dev.codesentinel.common.AuditService;
import dev.codesentinel.storage.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api")
public class SubmissionController {
    public record FileDto(long id, String path, long sizeBytes) {
    }

    public record SubmissionDto(long id, long assignmentId, int attempt, String status, Instant createdAt, Long studentId, String studentName,
                                String verdict, Integer passed, Integer total, List<FileDto> files, boolean synthetic) {
    }

    public record TestResultDto(String name, boolean sample, String verdict, long millis, String detail, String stdoutExcerpt, String stderrExcerpt) {
    }

    public record EvaluationDto(String status, String verdict, Integer passed, Integer total, String message, String compileLog, Long millis, List<TestResultDto> tests) {
    }

    private static final int MAX_ATTEMPTS = 50;
    private final JdbcClient jdbc;
    private final Access access;
    private final AuditService audit;
    private final FileStorageProvider storage;

    public SubmissionController(JdbcClient jdbc, Access access, AuditService audit, FileStorageProvider storage) {
        this.jdbc = jdbc;
        this.access = access;
        this.audit = audit;
        this.storage = storage;
    }

    // ---- upload -------------------------------------------------------------------------------------------------------

    @PostMapping(value = "/assignments/{assignmentId}/submissions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public SubmissionDto submit(@PathVariable long assignmentId, @RequestParam("file") MultipartFile upload) throws IOException {
        Principal p = CurrentUser.get();
        if (p.instructor()) throw ApiException.forbidden();
        access.requireAssignmentVisible(assignmentId, p);
        var a = jdbc.sql("SELECT language, deadline, kind, late_policy, late_until, max_attempts, available_from FROM assignments WHERE id=?").param(assignmentId)
                .query((rs, n) -> new Object[]{rs.getString(1), rs.getTimestamp(2).toInstant(), rs.getString(3), rs.getString(4), rs.getTimestamp(5), rs.getInt(6), rs.getTimestamp(7)}).single();
        if (!"CODING".equals(a[2])) throw ApiException.bad("This assignment is answered in the browser, not by uploading code");
        Instant now = Instant.now(), deadline = (Instant) a[1];
        if (a[6] != null && now.isBefore(((java.sql.Timestamp) a[6]).toInstant())) throw ApiException.conflict("This assignment is not open yet");
        boolean late = now.isAfter(deadline);
        if (late && ("BLOCK".equals(a[3]) || (a[4] != null && now.isAfter(((java.sql.Timestamp) a[4]).toInstant()))))
            throw ApiException.conflict("The deadline for this assignment has passed");
        String language = (String) a[0];
        int attempts = jdbc.sql("SELECT count(*) FROM submissions WHERE assignment_id=? AND student_id=?").params(assignmentId, p.id()).query(Integer.class).single();
        if (attempts >= Math.min(MAX_ATTEMPTS, (Integer) a[5])) throw ApiException.conflict("Attempt limit reached (" + Math.min(MAX_ATTEMPTS, (Integer) a[5]) + ")");
        if (upload.isEmpty()) throw ApiException.bad("Empty upload");

        String original = Optional.ofNullable(upload.getOriginalFilename()).orElse("");
        Map<String, byte[]> files = readUpload(original, upload);
        if (files.keySet().stream().noneMatch(f -> LanguageFiles.sourceExtensions(language).contains(LanguageFiles.ext(f))))
            throw ApiException.bad("No " + language + " source file found in the upload");

        int attempt = attempts + 1;
        long sid = jdbc.sql("INSERT INTO submissions(assignment_id, student_id, attempt, status, late) VALUES (?,?,?, 'QUEUED', ?) RETURNING id")
                .params(assignmentId, p.id(), attempt, late).query(Long.class).single();
        int i = 0;
        for (var e : files.entrySet()) {
            String key = "sub/" + sid + "/f" + (i++);
            StoredObject o = storage.store(new StoreRequest(key, new ByteArrayInputStream(e.getValue())));
            jdbc.sql("INSERT INTO submission_files(submission_id, path, storage_key, size_bytes, sha256) VALUES (?,?,?,?,?)")
                    .params(sid, e.getKey(), key, o.sizeBytes(), o.sha256()).update();
        }
        jdbc.sql("INSERT INTO evaluation_jobs(submission_id) VALUES (?)").param(sid).update();
        audit.log(p.id(), "SUBMISSION_CREATE", "submission", sid, "attempt " + attempt + ", " + files.size() + " files");
        return dto(sid, p);
    }

    private Map<String, byte[]> readUpload(String originalName, MultipartFile upload) throws IOException {
        Map<String, byte[]> out = new TreeMap<>();
        if (originalName.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            Path tmp = Files.createTempDirectory("cs-upload-");
            try (InputStream in = upload.getInputStream()) {
                List<String> names = SafeZipExtractor.extract(in, tmp.resolve("x"), SafeZipExtractor.Limits.defaults());
                for (String n : names) {
                    if (!LanguageFiles.ALLOWED.contains(LanguageFiles.ext(n))) throw ApiException.bad("File type not allowed in archive: ." + LanguageFiles.ext(n));
                    out.put(n, checkedText(Files.readAllBytes(tmp.resolve("x").resolve(n)), n));
                }
            } catch (SafeZipExtractor.UnsafeArchiveException e) {
                throw ApiException.bad("Rejected archive: " + e.getMessage());
            } finally {
                try (var w = Files.walk(tmp)) {
                    w.sorted(Comparator.reverseOrder()).forEach(q -> q.toFile().delete());
                }
            }
        } else {
            if (!originalName.matches("[A-Za-z0-9._+()-]{1,100}")) throw ApiException.bad("Invalid file name");
            if (!LanguageFiles.ALLOWED.contains(LanguageFiles.ext(originalName))) throw ApiException.bad("File type not allowed");
            if (upload.getSize() > 2L << 20) throw ApiException.bad("File too large (max 2 MiB)");
            out.put(originalName, checkedText(upload.getBytes(), originalName));
        }
        if (out.isEmpty()) throw ApiException.bad("No files in upload");
        return out;
    }

    private static byte[] checkedText(byte[] b, String name) {
        for (byte x : b) if (x == 0) throw ApiException.bad("Binary content is not allowed: " + name);
        return b;
    }

    // ---- reads --------------------------------------------------------------------------------------------------------

    private record Head(long id, long assignmentId, long studentId, long courseId) {
    }

    /** Owner or the course's instructor; everyone else gets 404. */
    private Head authorize(long submissionId, Principal p) {
        Head h = jdbc.sql("SELECT s.id, s.assignment_id, s.student_id, a.course_id FROM submissions s JOIN assignments a ON a.id=s.assignment_id WHERE s.id=?")
                .param(submissionId).query((rs, n) -> new Head(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4))).optional().orElseThrow(ApiException::notFound);
        boolean owner = !p.instructor() && h.studentId() == p.id();
        if (!owner && !access.ownsCourse(h.courseId(), p)) throw ApiException.notFound();
        return h;
    }

    SubmissionDto dto(long id, Principal viewer) {
        return jdbc.sql("""
                SELECT s.id, s.assignment_id, s.attempt, s.status, s.created_at, s.student_id, u.display_name, r.verdict, r.passed, r.total, s.synthetic
                FROM submissions s JOIN users u ON u.id=s.student_id LEFT JOIN evaluation_results r ON r.submission_id=s.id WHERE s.id=?""").param(id)
                .query((rs, n) -> {
                    boolean inst = viewer.instructor();
                    return new SubmissionDto(rs.getLong(1), rs.getLong(2), rs.getInt(3), rs.getString(4), rs.getTimestamp(5).toInstant(),
                            inst ? rs.getLong(6) : null, inst ? rs.getString(7) : null, rs.getString(8), (Integer) rs.getObject(9), (Integer) rs.getObject(10),
                            files(id), rs.getBoolean(11));
                }).optional().orElseThrow(ApiException::notFound);
    }

    private List<FileDto> files(long sid) {
        return jdbc.sql("SELECT id, path, size_bytes FROM submission_files WHERE submission_id=? ORDER BY path").param(sid)
                .query((rs, n) -> new FileDto(rs.getLong(1), rs.getString(2), rs.getLong(3))).list();
    }

    @GetMapping("/assignments/{assignmentId}/submissions/me")
    List<SubmissionDto> mine(@PathVariable long assignmentId) {
        Principal p = CurrentUser.get();
        if (p.instructor()) throw ApiException.forbidden();
        access.requireAssignmentVisible(assignmentId, p);
        return jdbc.sql("SELECT id FROM submissions WHERE assignment_id=? AND student_id=? ORDER BY attempt DESC").params(assignmentId, p.id()).query(Long.class).list()
                .stream().map(i -> dto(i, p)).toList();
    }

    @GetMapping("/assignments/{assignmentId}/submissions")
    List<SubmissionDto> all(@PathVariable long assignmentId, @RequestParam(required = false) String verdict, @RequestParam(required = false) String status,
                            @RequestParam(required = false) String q) {
        Principal p = CurrentUser.instructor();
        access.requireAssignmentOwner(assignmentId, p);
        String like = q == null || q.isBlank() ? null : "%" + q.trim().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        return jdbc.sql("""
                SELECT s.id FROM submissions s JOIN users u ON u.id=s.student_id LEFT JOIN evaluation_results r ON r.submission_id=s.id
                WHERE s.assignment_id=? AND (?::text IS NULL OR r.verdict=?) AND (?::text IS NULL OR s.status=?)
                AND (?::text IS NULL OR lower(u.display_name) LIKE ? OR lower(u.email) LIKE ?) ORDER BY s.created_at DESC, s.id DESC LIMIT 500""")
                .params(assignmentId, verdict, verdict, status, status, like, like, like).query(Long.class).list().stream().map(i -> dto(i, p)).toList();
    }

    @GetMapping("/submissions/{id}")
    SubmissionDto get(@PathVariable long id) {
        Principal p = CurrentUser.get();
        authorize(id, p);
        return dto(id, p);
    }

    @GetMapping("/submissions/{id}/files/{fileId}")
    ResponseEntity<byte[]> file(@PathVariable long id, @PathVariable long fileId) throws IOException {
        Principal p = CurrentUser.get();
        Head h = authorize(id, p);
        String key = jdbc.sql("SELECT storage_key FROM submission_files WHERE id=? AND submission_id=?").params(fileId, id).query(String.class).optional().orElseThrow(ApiException::notFound);
        if (p.instructor()) audit.log(p.id(), "SUBMISSION_FILE_VIEW", "submission", id, "file " + fileId);
        try (InputStream in = storage.read(key)) {
            return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/plain;charset=UTF-8")).header("X-Content-Type-Options", "nosniff").body(in.readAllBytes());
        }
    }

    @GetMapping("/submissions/{id}/evaluation")
    EvaluationDto evaluation(@PathVariable long id) {
        Principal p = CurrentUser.get();
        authorize(id, p);
        String status = jdbc.sql("SELECT status FROM submissions WHERE id=?").param(id).query(String.class).single();
        var r = jdbc.sql("SELECT id, verdict, passed, total, message, compile_log, millis FROM evaluation_results WHERE submission_id=?").param(id)
                .query((rs, n) -> new Object[]{rs.getLong(1), rs.getString(2), rs.getInt(3), rs.getInt(4), rs.getString(5), rs.getString(6), rs.getLong(7)}).optional();
        if (r.isEmpty()) return new EvaluationDto(status, null, null, null, null, null, null, List.of());
        boolean inst = p.instructor();
        // Students see per-test verdicts for every test, but detail and output excerpts only for SAMPLE tests (FR-JUDGE-11).
        List<TestResultDto> tests = jdbc.sql("SELECT name, sample, verdict, millis, detail, stdout_excerpt, stderr_excerpt FROM testcase_results WHERE result_id=? ORDER BY id").param(r.get()[0])
                .query((rs, n) -> {
                    boolean show = inst || rs.getBoolean(2);
                    return new TestResultDto(show ? rs.getString(1) : "Hidden test " + (n + 1), rs.getBoolean(2), rs.getString(3), rs.getLong(4),
                            show ? rs.getString(5) : null, show ? rs.getString(6) : null, show ? rs.getString(7) : null);
                }).list();
        Object[] o = r.get();
        return new EvaluationDto(status, (String) o[1], (Integer) o[2], (Integer) o[3], (String) o[4], (String) o[5], (Long) o[6], tests);
    }

    @PostMapping("/submissions/{id}/rejudge")
    @Transactional
    ResponseEntity<Void> rejudge(@PathVariable long id) {
        Principal p = CurrentUser.instructor();
        Head h = authorize(id, p);
        jdbc.sql("DELETE FROM evaluation_results WHERE submission_id=?").param(id).update();
        jdbc.sql("UPDATE submissions SET status='QUEUED' WHERE id=?").param(id).update();
        jdbc.sql("INSERT INTO evaluation_jobs(submission_id) VALUES (?)").param(id).update();
        audit.log(p.id(), "SUBMISSION_REJUDGE", "submission", id, null);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }
}
