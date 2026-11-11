package dev.codesentinel.quiz;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.codesentinel.auth.CurrentUser;
import dev.codesentinel.auth.Principal;
import dev.codesentinel.common.Access;
import dev.codesentinel.common.ApiException;
import dev.codesentinel.common.AuditService;
import dev.codesentinel.storage.FileStorageProvider;
import dev.codesentinel.storage.StoreRequest;
import jakarta.validation.Valid;
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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/**
 * Multi-question assignments. Instructors of the course author questions, see every attempt and mark; enrolled students take
 * the assessment and see their marks once released. Answer keys and explanations never reach a student before release.
 */
@RestController
@RequestMapping("/api")
public class QuizController {
    record GradeRequest(List<QuizService.ManualMark> marks, @Size(max = 5000) String feedback, @Size(max = 5000) String privateNote, Boolean release, boolean returnForRevision) {
    }

    record SaveRequest(Map<Long, JsonNode> answers) {
    }

    private static final long MAX_UPLOAD = 5L << 20;
    private final QuizService quiz;
    private final JdbcClient jdbc;
    private final Access access;
    private final AuditService audit;
    private final ObjectMapper om;
    private final FileStorageProvider storage;
    private final dev.codesentinel.notify.NotificationService notes;

    public QuizController(QuizService quiz, JdbcClient jdbc, Access access, AuditService audit, ObjectMapper om, FileStorageProvider storage, dev.codesentinel.notify.NotificationService notes) {
        this.notes = notes;
        this.quiz = quiz;
        this.jdbc = jdbc;
        this.access = access;
        this.audit = audit;
        this.om = om;
        this.storage = storage;
    }

    // ------------------------------------------------------------------ authoring (course instructors)

    @GetMapping("/assignments/{id}/questions")
    List<QuizService.Question> questions(@PathVariable long id) {
        access.requireAssignmentOwner(id, CurrentUser.instructor());
        return quiz.questions(id);
    }

    @PutMapping("/assignments/{id}/questions")
    List<QuizService.Question> replace(@PathVariable long id, @RequestBody List<QuizService.QuestionInput> body) {
        Principal p = CurrentUser.instructor();
        access.requireAssignmentOwner(id, p);
        var out = quiz.replace(id, body);
        audit.log(p.id(), "QUESTIONS_SAVE", "assignment", id, out.size() + " questions");
        return out;
    }

    /** The assessment exactly as a student would see it, without starting an attempt. */
    @GetMapping("/assignments/{id}/preview")
    ObjectNode preview(@PathVariable long id) {
        access.requireAssignmentOwner(id, CurrentUser.instructor());
        var s = quiz.settings(id);
        ObjectNode o = om.createObjectNode();
        ArrayNode qs = o.putArray("questions");
        List<QuizService.Question> list = new ArrayList<>(quiz.questions(id));
        if (s.shuffleQuestions()) Collections.shuffle(list, new Random(42));
        for (var q : list) qs.add(quiz.studentView(q, 0, s.shuffleOptions()));
        o.put("quizMinutes", s.quizMinutes());
        return o;
    }

    @GetMapping("/assignments/{id}/attempts")
    List<Map<String, Object>> attempts(@PathVariable long id) {
        access.requireAssignmentOwner(id, CurrentUser.instructor());
        return jdbc.sql("""
                SELECT a.id, a.student_id, u.display_name, u.email, a.attempt, a.status, a.started_at, a.submitted_at, a.late, a.score, a.released,
                  (SELECT count(*) FROM question_responses r WHERE r.attempt_id=a.id AND r.auto_score IS NULL AND r.manual_score IS NULL)
                FROM quiz_attempts a JOIN users u ON u.id=a.student_id WHERE a.assignment_id=? ORDER BY u.display_name, a.attempt DESC LIMIT 2000""").param(id)
                .query((rs, n) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getLong(1));
                    m.put("studentId", rs.getLong(2));
                    m.put("studentName", rs.getString(3));
                    m.put("email", rs.getString(4));
                    m.put("attempt", rs.getInt(5));
                    m.put("status", rs.getString(6));
                    m.put("startedAt", rs.getTimestamp(7).toInstant());
                    m.put("submittedAt", QuizService.ts(rs, 8));
                    m.put("late", rs.getBoolean(9));
                    m.put("score", rs.getBigDecimal(10));
                    m.put("released", rs.getBoolean(11));
                    m.put("needsMarking", rs.getInt(12));
                    return m;
                }).list();
    }

    @PostMapping("/attempts/{id}/grade")
    ObjectNode grade(@PathVariable long id, @Valid @RequestBody GradeRequest r) {
        Principal p = CurrentUser.instructor();
        var a = quiz.attempt(id);
        access.requireAssignmentOwner(a.assignmentId(), p);
        var after = quiz.grade(id, r.marks() == null ? List.of() : r.marks(), r.feedback(), r.privateNote(), r.release(), r.returnForRevision());
        audit.log(p.id(), "ATTEMPT_GRADE", "quiz_attempt", id, null);
        if (after.released()) notifyReleased(after.id());
        return view(id, p);
    }

    /** Releases every fully marked attempt. Attempts still waiting for a marker stay hidden. */
    @PostMapping("/assignments/{id}/release")
    Map<String, Integer> release(@PathVariable long id) {
        Principal p = CurrentUser.instructor();
        access.requireAssignmentOwner(id, p);
        List<Long> ids = jdbc.sql("UPDATE quiz_attempts SET released=TRUE WHERE assignment_id=? AND status IN ('GRADED','RETURNED') AND NOT released RETURNING id").param(id).query(Long.class).list();
        int n = ids.size();
        ids.forEach(this::notifyReleased);
        audit.log(p.id(), "ATTEMPTS_RELEASE", "assignment", id, n + " released");
        return Map.of("released", n);
    }

    @PostMapping("/assignments/{id}/regrade")
    Map<String, Integer> regrade(@PathVariable long id) {
        Principal p = CurrentUser.instructor();
        access.requireAssignmentOwner(id, p);
        int n = quiz.regrade(id);
        audit.log(p.id(), "ATTEMPTS_REGRADE", "assignment", id, n + " attempts");
        return Map.of("regraded", n);
    }

    private void notifyReleased(long attemptId) {
        jdbc.sql("SELECT q.student_id, a.title, q.status, a.id FROM quiz_attempts q JOIN assignments a ON a.id=q.assignment_id WHERE q.id=?").param(attemptId)
                .query((rs, n) -> {
                    notes.notifyUser(rs.getLong(1), "GRADE", (rs.getString(3).equals("RETURNED") ? "Returned for revision: " : "Marks released: ") + rs.getString(2), "", "/attempts/" + attemptId, "att-" + attemptId + "-" + rs.getString(3));
                    return 0;
                }).list();
    }

    // ------------------------------------------------------------------ taking the assessment (students)

    private QuizService.Attempt ownAttempt(long id, Principal p) {
        var a = quiz.attempt(id);
        if (p.instructor() || a.studentId() != p.id()) throw ApiException.notFound();
        access.requireAssignmentVisible(a.assignmentId(), p);
        return a;
    }

    @PostMapping("/assignments/{id}/attempts")
    ObjectNode start(@PathVariable long id) {
        Principal p = CurrentUser.get();
        if (p.instructor()) throw ApiException.forbidden();
        access.requireAssignmentVisible(id, p);
        var a = quiz.start(id, p.id(), Instant.now());
        audit.log(p.id(), "ATTEMPT_START", "quiz_attempt", a.id(), null);
        return view(a.id(), p);
    }

    @GetMapping("/assignments/{id}/attempts/me")
    List<Map<String, Object>> mine(@PathVariable long id) {
        Principal p = CurrentUser.get();
        if (p.instructor()) throw ApiException.forbidden();
        access.requireAssignmentVisible(id, p);
        var s = quiz.settings(id);
        Instant now = Instant.now();
        List<Map<String, Object>> out = new ArrayList<>();
        for (var a : quiz.attempts(id, p.id())) {
            if (quiz.expired(a, s, now)) a = quiz.submit(a.id(), now);
            boolean vis = quiz.visibleToStudent(a, s, now);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.id());
            m.put("attempt", a.attempt());
            m.put("status", a.status());
            m.put("startedAt", a.startedAt());
            m.put("submittedAt", a.submittedAt());
            m.put("late", a.late());
            m.put("score", vis ? a.score() : null);
            m.put("released", vis);
            out.add(m);
        }
        return out;
    }

    @GetMapping("/attempts/{id}")
    ObjectNode get(@PathVariable long id) {
        Principal p = CurrentUser.get();
        var a = quiz.attempt(id);
        if (p.instructor()) access.requireAssignmentOwner(a.assignmentId(), p);
        else ownAttempt(id, p);
        if (!p.instructor() && quiz.expired(a, quiz.settings(a.assignmentId()), Instant.now())) quiz.submit(id, Instant.now());
        return view(id, p);
    }

    @PutMapping("/attempts/{id}/responses")
    ObjectNode save(@PathVariable long id, @RequestBody SaveRequest r) {
        Principal p = CurrentUser.get();
        ownAttempt(id, p);
        quiz.save(id, r.answers() == null ? Map.of() : r.answers(), Instant.now());
        ObjectNode o = om.createObjectNode();
        o.put("savedAt", Instant.now().toString());
        return o;
    }

    @PostMapping("/attempts/{id}/submit")
    ObjectNode submit(@PathVariable long id) {
        Principal p = CurrentUser.get();
        ownAttempt(id, p);
        quiz.submit(id, Instant.now());
        audit.log(p.id(), "ATTEMPT_SUBMIT", "quiz_attempt", id, null);
        return view(id, p);
    }

    @PostMapping(value = "/attempts/{id}/files/{questionId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ObjectNode upload(@PathVariable long id, @PathVariable long questionId, @RequestParam("file") MultipartFile file) throws IOException {
        Principal p = CurrentUser.get();
        var a = ownAttempt(id, p);
        String type = jdbc.sql("SELECT type FROM questions WHERE id=? AND assignment_id=?").params(questionId, a.assignmentId()).query(String.class).optional().orElseThrow(ApiException::notFound);
        if (!type.equals("FILE")) throw ApiException.bad("This question does not take a file");
        if (file.isEmpty() || file.getSize() > MAX_UPLOAD) throw ApiException.bad("The file must be between 1 byte and 5 MiB");
        String name = file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename().replaceAll("[^A-Za-z0-9._()+-]", "_");
        if (name.length() > 120) name = name.substring(name.length() - 120);
        String key = "quiz/" + id + "/" + questionId + "/" + UUID.randomUUID();
        var stored = storage.store(new StoreRequest(key, file.getInputStream()));
        ObjectNode resp = om.createObjectNode().put("fileName", name).put("size", stored.sizeBytes()).put("key", key);
        quiz.save(id, Map.of(questionId, resp), Instant.now());
        return view(id, p);
    }

    @GetMapping("/attempts/{id}/files/{questionId}")
    ResponseEntity<InputStreamResource> download(@PathVariable long id, @PathVariable long questionId) {
        Principal p = CurrentUser.get();
        var a = quiz.attempt(id);
        if (p.instructor()) access.requireAssignmentOwner(a.assignmentId(), p);
        else ownAttempt(id, p);
        JsonNode r = quiz.responses(id).stream().filter(x -> x.questionId() == questionId).findFirst().map(QuizService.Response::response).orElseThrow(ApiException::notFound);
        if (!r.has("key")) throw ApiException.notFound();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(r.path("fileName").asText("upload")).build().toString())
                .header("X-Content-Type-Options", "nosniff").body(new InputStreamResource(storage.read(r.get("key").asText())));
    }

    // ------------------------------------------------------------------ the one view both roles read

    private ObjectNode view(long attemptId, Principal p) {
        var a = quiz.attempt(attemptId);
        var s = quiz.settings(a.assignmentId());
        Instant now = Instant.now();
        boolean staff = p.instructor();
        boolean visible = staff || quiz.visibleToStudent(a, s, now);
        boolean reveal = staff || (visible && s.showAnswers());
        ObjectNode o = om.createObjectNode();
        o.put("id", a.id()).put("assignmentId", a.assignmentId()).put("attempt", a.attempt()).put("status", a.status()).put("late", a.late())
                .put("startedAt", a.startedAt().toString()).put("submittedAt", a.submittedAt() == null ? null : a.submittedAt().toString())
                .put("visible", visible).put("maxMarks", s.maxMarks()).put("score", visible ? a.score() : null)
                .put("feedback", visible ? a.feedback() : "").put("serverNow", now.toString());
        Instant end = quiz.endsAt(a, s);
        o.put("endsAt", end == null ? null : end.toString());
        if (staff) {
            o.put("privateNote", a.privateNote()).put("released", a.released());
            jdbc.sql("SELECT display_name, email FROM users WHERE id=?").param(a.studentId()).query((rs, n) -> o.put("studentName", rs.getString(1)).put("email", rs.getString(2))).single();
        }
        Map<Long, QuizService.Response> rs = new HashMap<>();
        for (var r : quiz.responses(attemptId)) rs.put(r.questionId(), r);
        Map<Long, QuizService.Question> qs = new LinkedHashMap<>();
        for (var q : quiz.questions(a.assignmentId())) qs.put(q.id(), q);
        List<Long> order = new ArrayList<>(a.order());
        for (Long id : qs.keySet()) if (!order.contains(id)) order.add(id); // questions added after the attempt began
        Map<Long, String[]> judge = new HashMap<>();
        jdbc.sql("SELECT question_id, judge_status, judge_result::text FROM question_responses WHERE attempt_id=? AND (judge_status IS NOT NULL OR judge_result IS NOT NULL)").param(attemptId)
                .query((x, n) -> judge.put(x.getLong(1), new String[]{x.getString(2), x.getString(3)})).list();
        ArrayNode arr = o.putArray("questions");
        for (Long qid : order) {
            var q = qs.get(qid);
            if (q == null) continue;
            ObjectNode v = quiz.studentView(q, a.id(), s.shuffleOptions());
            var r = rs.get(qid);
            JsonNode resp = r == null ? om.createObjectNode() : r.response().deepCopy();
            if (resp.isObject()) ((ObjectNode) resp).remove("key");
            v.set("response", resp);
            if (visible) {
                v.put("autoScore", r == null ? null : r.autoScore()).put("manualScore", r == null ? null : r.manualScore()).put("feedback", r == null ? "" : r.feedback());
            }
            if (q.type().equals("CODE") && judge.containsKey(qid)) {
                String[] j = judge.get(qid);
                ObjectNode jn = v.putObject("judge");
                jn.put("status", j[0] == null ? "FAILED" : j[0]);
                if ((visible || staff) && j[1] != null) try {
                    jn.set("result", om.readTree(j[1]));
                } catch (Exception ignored) {
                }
            }
            if (reveal && !a.status().equals("IN_PROGRESS") || staff) {
                if (q.type().equals("CODE") && !staff) {
                    ObjectNode safe = om.createObjectNode();
                    ArrayNode t = safe.putArray("tests");
                    for (JsonNode x : q.answer().path("tests")) if (x.path("sample").asBoolean(false)) t.add(x); // hidden tests stay hidden, even after release
                    v.set("answer", safe);
                } else v.set("answer", q.answer());
                v.put("explanation", q.explanation());
            }
            arr.add(v);
        }
        return o;
    }
}
