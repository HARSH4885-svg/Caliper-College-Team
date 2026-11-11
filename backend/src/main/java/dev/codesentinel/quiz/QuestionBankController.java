package dev.codesentinel.quiz;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.codesentinel.auth.CurrentUser;
import dev.codesentinel.auth.Principal;
import dev.codesentinel.common.Access;
import dev.codesentinel.common.ApiException;
import dev.codesentinel.common.AuditService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Each instructor's reusable questions, organised by subject, topic and difficulty. Private to the owner. */
@RestController
@RequestMapping("/api")
public class QuestionBankController {
    public record BankQuestion(long id, String type, String prompt, BigDecimal marks, String difficulty, String subject, String topic, JsonNode options,
                               JsonNode answer, JsonNode settings, String explanation, Instant createdAt, int usedIn) {
    }

    record BankInput(String type, String prompt, BigDecimal marks, String difficulty, String subject, String topic, JsonNode options, JsonNode answer,
                     JsonNode settings, String explanation) {
        QuizService.QuestionInput asQuestion() {
            return new QuizService.QuestionInput(null, type, prompt, marks, difficulty, true, options, answer, settings, explanation, null);
        }
    }

    record RandomRequest(String subject, String topic, String difficulty, String type, @Min(1) @Max(50) int count) {
    }

    private final JdbcClient jdbc;
    private final ObjectMapper om;
    private final QuizService quiz;
    private final Access access;
    private final AuditService audit;

    public QuestionBankController(JdbcClient jdbc, ObjectMapper om, QuizService quiz, Access access, AuditService audit) {
        this.jdbc = jdbc;
        this.om = om;
        this.quiz = quiz;
        this.access = access;
        this.audit = audit;
    }

    private JsonNode parse(String s) {
        try {
            return om.readTree(s);
        } catch (Exception e) {
            return om.createObjectNode();
        }
    }

    private String json(JsonNode n, String d) {
        try {
            return n == null || n.isNull() ? d : om.writeValueAsString(n);
        } catch (Exception e) {
            throw ApiException.bad("Invalid JSON");
        }
    }

    static String like(String s) {
        return s == null || s.isBlank() ? null : "%" + s.trim().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    @GetMapping("/question-bank")
    List<BankQuestion> list(@RequestParam(required = false) String q, @RequestParam(required = false) String type, @RequestParam(required = false) String difficulty,
                            @RequestParam(required = false) String subject, @RequestParam(required = false) String topic) {
        Principal p = CurrentUser.instructor();
        String l = like(q);
        return jdbc.sql("""
                SELECT b.id, b.type, b.prompt, b.marks, b.difficulty, b.subject, b.topic, b.options::text, b.answer::text, b.settings::text, b.explanation, b.created_at,
                  (SELECT count(*) FROM questions x WHERE x.bank_id=b.id)
                FROM question_bank b WHERE b.owner_id=? AND (?::text IS NULL OR lower(b.prompt) LIKE ? OR lower(b.topic) LIKE ? OR lower(b.subject) LIKE ?)
                AND (?::text IS NULL OR b.type=?) AND (?::text IS NULL OR b.difficulty=?) AND (?::text IS NULL OR b.subject=?) AND (?::text IS NULL OR b.topic=?)
                ORDER BY b.subject, b.topic, b.id DESC LIMIT 500""")
                .params(p.id(), l, l, l, l, blank(type), blank(type), blank(difficulty), blank(difficulty), blank(subject), blank(subject), blank(topic), blank(topic))
                .query((rs, n) -> new BankQuestion(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getBigDecimal(4), rs.getString(5), rs.getString(6), rs.getString(7),
                        parse(rs.getString(8)), parse(rs.getString(9)), parse(rs.getString(10)), rs.getString(11), rs.getTimestamp(12).toInstant(), rs.getInt(13))).list();
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private void check(BankInput b) {
        quiz.validate(b.asQuestion(), 0);
        if (b.subject() != null && b.subject().length() > 80 || b.topic() != null && b.topic().length() > 80) throw ApiException.bad("Subject and topic are at most 80 characters");
    }

    @PostMapping("/question-bank")
    BankQuestion create(@RequestBody BankInput b) {
        Principal p = CurrentUser.instructor();
        check(b);
        long id = jdbc.sql("""
                INSERT INTO question_bank(owner_id, type, prompt, marks, difficulty, subject, topic, options, answer, settings, explanation)
                VALUES (?,?,?,?,?,?,?,?::jsonb,?::jsonb,?::jsonb,?) RETURNING id""")
                .params(p.id(), b.type(), b.prompt().strip(), b.marks(), b.difficulty() == null ? "MEDIUM" : b.difficulty(), nz(b.subject()), nz(b.topic()),
                        json(b.options(), "[]"), json(b.answer(), "{}"), json(b.settings(), "{}"), nz(b.explanation())).query(Long.class).single();
        audit.log(p.id(), "BANK_CREATE", "bank_question", id, null);
        return one(id, p);
    }

    @PutMapping("/question-bank/{id}")
    BankQuestion update(@PathVariable long id, @RequestBody BankInput b) {
        Principal p = CurrentUser.instructor();
        one(id, p);
        check(b);
        jdbc.sql("UPDATE question_bank SET type=?, prompt=?, marks=?, difficulty=?, subject=?, topic=?, options=?::jsonb, answer=?::jsonb, settings=?::jsonb, explanation=? WHERE id=?")
                .params(b.type(), b.prompt().strip(), b.marks(), b.difficulty() == null ? "MEDIUM" : b.difficulty(), nz(b.subject()), nz(b.topic()), json(b.options(), "[]"),
                        json(b.answer(), "{}"), json(b.settings(), "{}"), nz(b.explanation()), id).update();
        return one(id, p);
    }

    /** Questions already copied into assignments keep their own copy; deleting from the bank does not touch them. */
    @DeleteMapping("/question-bank/{id}")
    ResponseEntity<Void> delete(@PathVariable long id) {
        Principal p = CurrentUser.instructor();
        one(id, p);
        jdbc.sql("DELETE FROM question_bank WHERE id=?").param(id).update();
        audit.log(p.id(), "BANK_DELETE", "bank_question", id, null);
        return ResponseEntity.noContent().build();
    }

    private static String nz(String s) {
        return s == null ? "" : s.trim();
    }

    private BankQuestion one(long id, Principal p) {
        return list(null, null, null, null, null).stream().filter(b -> b.id() == id).findFirst().orElseThrow(ApiException::notFound);
    }

    /** Adds {@code count} questions drawn at random from the bank (matching the filters) to the end of an assignment. */
    @PostMapping("/assignments/{id}/questions/random")
    @Transactional
    List<QuizService.Question> random(@PathVariable long id, @RequestBody RandomRequest r) {
        Principal p = CurrentUser.instructor();
        access.requireAssignmentOwner(id, p);
        if (r.count() < 1 || r.count() > 50) throw ApiException.bad("Choose between 1 and 50 questions");
        var pool = new ArrayList<>(list(null, r.type(), r.difficulty(), r.subject(), r.topic()));
        var already = jdbc.sql("SELECT bank_id FROM questions WHERE assignment_id=? AND bank_id IS NOT NULL").param(id).query(Long.class).list();
        pool.removeIf(b -> already.contains(b.id()));
        if (pool.size() < r.count()) throw ApiException.conflict("Only " + pool.size() + " unused bank question" + (pool.size() == 1 ? "" : "s") + " match these filters");
        java.util.Collections.shuffle(pool);
        List<QuizService.QuestionInput> all = new ArrayList<>();
        for (var q : quiz.questions(id))
            all.add(new QuizService.QuestionInput(q.id(), q.type(), q.prompt(), q.marks(), q.difficulty(), q.required(), q.options(), q.answer(), q.settings(), q.explanation(), q.bankId()));
        for (var b : pool.subList(0, r.count()))
            all.add(new QuizService.QuestionInput(null, b.type(), b.prompt(), b.marks(), b.difficulty(), true, b.options(), b.answer(), b.settings(), b.explanation(), b.id()));
        var out = quiz.replace(id, all);
        audit.log(p.id(), "QUESTIONS_RANDOM", "assignment", id, r.count() + " from bank");
        return out;
    }
}
