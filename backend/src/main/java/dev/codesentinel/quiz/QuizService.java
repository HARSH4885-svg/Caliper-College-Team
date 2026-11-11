package dev.codesentinel.quiz;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.codesentinel.common.ApiException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

/**
 * Question authoring, attempts and marking for multi-question assignments. Controllers do the permission checks; this class
 * enforces the assessment rules (availability window, attempt limit, late policy, time limit) and keeps scores consistent.
 */
@Service
public class QuizService {
    public record QuestionInput(Long id, String type, String prompt, BigDecimal marks, String difficulty, boolean required, JsonNode options,
                                JsonNode answer, JsonNode settings, String explanation, Long bankId) {
    }

    public record Question(long id, int ord, String type, String prompt, BigDecimal marks, String difficulty, boolean required, JsonNode options,
                           JsonNode answer, JsonNode settings, String explanation, Long bankId) {
    }

    public record Settings(long id, long courseId, String kind, Instant deadline, Instant availableFrom, int maxAttempts, String latePolicy, int latePenaltyPct,
                           Instant lateUntil, Integer quizMinutes, boolean shuffleQuestions, boolean shuffleOptions, String releaseMode, boolean showAnswers,
                           BigDecimal maxMarks, boolean published) {
    }

    public record Attempt(long id, long assignmentId, long studentId, int attempt, String status, Instant startedAt, Instant submittedAt, boolean late,
                          BigDecimal score, boolean released, String feedback, String privateNote, List<Long> order) {
    }

    public record Response(long questionId, JsonNode response, BigDecimal autoScore, BigDecimal manualScore, String feedback) {
    }

    static final Set<String> DIFFICULTY = Set.of("EASY", "MEDIUM", "HARD");
    /** Saves arriving this long after a timed attempt runs out are still accepted (network delay); later ones are refused. */
    static final long GRACE_SECONDS = 30;

    private final JdbcClient jdbc;
    private final ObjectMapper om;
    private final QuizGrader quizGrader;

    public QuizService(JdbcClient jdbc, ObjectMapper om, QuizGrader quizGrader) {
        this.jdbc = java.util.Objects.requireNonNull(jdbc, "jdbc must not be null");
        this.om = java.util.Objects.requireNonNull(om, "om must not be null");
        this.quizGrader = java.util.Objects.requireNonNull(quizGrader, "quizGrader must not be null");
    }

    // ------------------------------------------------------------------ authoring

    public void validate(QuestionInput q, int index) {
        String where = "Question " + (index + 1) + ": ";
        if (q.type() == null || !quizGrader.isSupported(q.type())) throw ApiException.bad(where + "unknown type");
        if (q.prompt() == null || q.prompt().isBlank()) throw ApiException.bad(where + "the question text is empty");
        if (q.prompt().length() > 20000) throw ApiException.bad(where + "the question text is too long");
        if (q.marks() == null || q.marks().signum() < 0 || q.marks().compareTo(BigDecimal.valueOf(1000)) > 0) throw ApiException.bad(where + "marks must be between 0 and 1000");
        if (q.difficulty() != null && !DIFFICULTY.contains(q.difficulty())) throw ApiException.bad(where + "difficulty must be EASY, MEDIUM or HARD");
        if (q.explanation() != null && q.explanation().length() > 5000) throw ApiException.bad(where + "the explanation is too long");
        JsonNode opts = q.options() == null ? null : q.options();
        JsonNode st = q.settings() == null ? com.fasterxml.jackson.databind.node.MissingNode.getInstance() : q.settings();
        JsonNode ans = q.answer() == null ? com.fasterxml.jackson.databind.node.NullNode.getInstance() : q.answer();
        switch (q.type()) {
            case "MCQ", "MULTI" -> {
                if (opts == null || !opts.isArray() || opts.size() < 2 || opts.size() > 10) throw ApiException.bad(where + "give between 2 and 10 options");
                for (JsonNode o : opts) if (!o.isTextual() || o.asText().isBlank() || o.asText().length() > 1000) throw ApiException.bad(where + "every option needs text");
                JsonNode c = ans.path("correct");
                if (!c.isArray() || c.isEmpty()) throw ApiException.bad(where + "mark the correct option");
                if (q.type().equals("MCQ") && c.size() != 1) throw ApiException.bad(where + "a multiple-choice question has exactly one correct option");
                for (JsonNode i : c) if (!i.canConvertToInt() || i.asInt() < 0 || i.asInt() >= opts.size()) throw ApiException.bad(where + "the correct option is out of range");
            }
            case "TRUE_FALSE" -> {
                if (!ans.path("value").isBoolean()) throw ApiException.bad(where + "choose whether the statement is true or false");
            }
            case "FILL_BLANK" -> {
                JsonNode b = ans.path("blanks");
                if (!b.isArray() || b.isEmpty() || b.size() > 20) throw ApiException.bad(where + "give the accepted answers for each blank");
                for (JsonNode blank : b) if (!blank.isArray() || blank.isEmpty() || blank.get(0).asText().isBlank()) throw ApiException.bad(where + "every blank needs at least one accepted answer");
                int holes = q.prompt().split("___", -1).length - 1;
                if (holes != b.size()) throw ApiException.bad(where + "the text has " + holes + " blank(s) written as ___ but " + b.size() + " answer(s)");
            }
            case "OUTPUT" -> {
                if (!st.path("code").isTextual() || st.path("code").asText().isBlank()) throw ApiException.bad(where + "add the code whose output students predict");
                if (!ans.path("accepted").isArray() || ans.path("accepted").isEmpty()) throw ApiException.bad(where + "give the expected output");
            }
            case "CODE" -> {
                if (!Set.of("python", "java", "c", "cpp", "javascript").contains(st.path("language").asText(""))) throw ApiException.bad(where + "choose the programming language");
                JsonNode tests = ans.path("tests");
                if (!tests.isArray() || tests.isEmpty() || tests.size() > 30) throw ApiException.bad(where + "add between 1 and 30 test cases");
                for (JsonNode t : tests) if (!t.path("expected").isTextual()) throw ApiException.bad(where + "every test needs an expected output");
                if (st.path("starter").asText("").length() > 20000) throw ApiException.bad(where + "the starter code is too long");
            }
            default -> { /* SHORT, LONG, FILE: free text, optional answer key */ }
        }
    }

    private String json(JsonNode n, String dflt) {
        try {
            return n == null || n.isNull() || n.isMissingNode() ? dflt : om.writeValueAsString(n);
        } catch (Exception e) {
            throw ApiException.bad("Invalid JSON");
        }
    }

    private JsonNode parse(String s) {
        try {
            return om.readTree(s == null ? "{}" : s);
        } catch (Exception e) {
            return om.createObjectNode();
        }
    }

    private Question mapQ(ResultSet rs) throws SQLException {
        long bank = rs.getLong(12);
        return new Question(rs.getLong(1), rs.getInt(2), rs.getString(3), rs.getString(4), rs.getBigDecimal(5), rs.getString(6), rs.getBoolean(7),
                parse(rs.getString(8)), parse(rs.getString(9)), parse(rs.getString(10)), rs.getString(11), rs.wasNull() ? null : bank);
    }

    public List<Question> questions(long assignmentId) {
        return jdbc.sql("SELECT id, ord, type, prompt, marks, difficulty, required, options::text, answer::text, settings::text, explanation, bank_id FROM questions WHERE assignment_id=? ORDER BY ord, id")
                .param(assignmentId).query((rs, n) -> mapQ(rs)).list();
    }

    /**
     * Saves the builder's list in order: rows with an id are updated, new rows inserted, missing rows removed. A question that
     * students have already answered cannot be removed (its marks can be set to 0 instead). Submitted attempts are re-marked,
     * because a changed answer key must change everyone's score the same way.
     */
    @Transactional
    public List<Question> replace(long assignmentId, List<QuestionInput> in) {
        if (in.size() > 200) throw ApiException.bad("At most 200 questions per assignment");
        for (int i = 0; i < in.size(); i++) validate(in.get(i), i);
        Set<Long> keep = new HashSet<>();
        for (QuestionInput q : in) if (q.id() != null) keep.add(q.id());
        Set<Long> existing = new HashSet<>(jdbc.sql("SELECT id FROM questions WHERE assignment_id=?").param(assignmentId).query(Long.class).list());
        for (Long id : keep) if (!existing.contains(id)) throw ApiException.bad("Question " + id + " does not belong to this assignment");
        for (Long id : existing) {
            if (keep.contains(id)) continue;
            boolean answered = jdbc.sql("SELECT count(*) FROM question_responses r JOIN quiz_attempts a ON a.id=r.attempt_id WHERE r.question_id=? AND a.status<>'IN_PROGRESS'").param(id).query(Long.class).single() > 0;
            if (answered) throw ApiException.conflict("Students have already answered a question you removed. Set its marks to 0 instead of deleting it.");
            jdbc.sql("DELETE FROM questions WHERE id=?").param(id).update();
        }
        int ord = 0;
        for (QuestionInput q : in) {
            Object[] p = {q.type(), q.prompt().strip(), q.marks(), q.difficulty() == null ? "MEDIUM" : q.difficulty(), q.required(), json(q.options(), "[]"), json(q.answer(), "{}"),
                    json(q.settings(), "{}"), q.explanation() == null ? "" : q.explanation(), q.bankId(), ord++};
            if (q.id() == null) {
                jdbc.sql("INSERT INTO questions(type, prompt, marks, difficulty, required, options, answer, settings, explanation, bank_id, ord, assignment_id) VALUES (?,?,?,?,?,?::jsonb,?::jsonb,?::jsonb,?,?,?,?)")
                        .params(concat(p, assignmentId)).update();
            } else {
                jdbc.sql("UPDATE questions SET type=?, prompt=?, marks=?, difficulty=?, required=?, options=?::jsonb, answer=?::jsonb, settings=?::jsonb, explanation=?, bank_id=?, ord=? WHERE id=?")
                        .params(concat(p, q.id())).update();
            }
        }
        BigDecimal total = jdbc.sql("SELECT coalesce(sum(marks),0) FROM questions WHERE assignment_id=?").param(assignmentId).query(BigDecimal.class).single();
        if (total.signum() > 0) jdbc.sql("UPDATE assignments SET max_marks=? WHERE id=? AND kind='QUIZ'").params(total, assignmentId).update();
        regrade(assignmentId);
        return questions(assignmentId);
    }

    private static Object[] concat(Object[] a, Object b) {
        Object[] r = Arrays.copyOf(a, a.length + 1);
        r[a.length] = b;
        return r;
    }

    // ------------------------------------------------------------------ settings and attempts

    public Settings settings(long assignmentId) {
        return jdbc.sql("""
                SELECT id, course_id, kind, deadline, available_from, max_attempts, late_policy, late_penalty_pct, late_until, quiz_minutes,
                       shuffle_questions, shuffle_options, release_mode, show_answers, max_marks, published FROM assignments WHERE id=?""").param(assignmentId)
                .query((rs, n) -> new Settings(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getTimestamp(4).toInstant(), ts(rs, 5), rs.getInt(6), rs.getString(7), rs.getInt(8),
                        ts(rs, 9), (Integer) rs.getObject(10), rs.getBoolean(11), rs.getBoolean(12), rs.getString(13), rs.getBoolean(14), rs.getBigDecimal(15), rs.getBoolean(16)))
                .optional().orElseThrow(ApiException::notFound);
    }

    static Instant ts(ResultSet rs, int i) throws SQLException {
        Timestamp t = rs.getTimestamp(i);
        return t == null ? null : t.toInstant();
    }

    private Attempt mapA(ResultSet rs) throws SQLException {
        List<Long> order = new ArrayList<>();
        for (JsonNode n : parse(rs.getString(13))) order.add(n.asLong());
        return new Attempt(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getInt(4), rs.getString(5), rs.getTimestamp(6).toInstant(), ts(rs, 7), rs.getBoolean(8),
                rs.getBigDecimal(9), rs.getBoolean(10), rs.getString(11), rs.getString(12), order);
    }

    private static final String A_COLS = "id, assignment_id, student_id, attempt, status, started_at, submitted_at, late, score, released, feedback, private_note, question_order::text";

    public Attempt attempt(long id) {
        return jdbc.sql("SELECT " + A_COLS + " FROM quiz_attempts WHERE id=?").param(id).query((rs, n) -> mapA(rs)).optional().orElseThrow(ApiException::notFound);
    }

    public List<Attempt> attempts(long assignmentId, Long studentId) {
        return jdbc.sql("SELECT " + A_COLS + " FROM quiz_attempts WHERE assignment_id=? AND (?::bigint IS NULL OR student_id=?) ORDER BY student_id, attempt DESC")
                .params(assignmentId, studentId, studentId).query((rs, n) -> mapA(rs)).list();
    }

    public List<Response> responses(long attemptId) {
        return jdbc.sql("SELECT question_id, response::text, auto_score, manual_score, feedback FROM question_responses WHERE attempt_id=?").param(attemptId)
                .query((rs, n) -> new Response(rs.getLong(1), parse(rs.getString(2)), rs.getBigDecimal(3), rs.getBigDecimal(4), rs.getString(5))).list();
    }

    /** When a timed attempt must end: the earlier of start + time limit and the last moment a submission is accepted. */
    public Instant endsAt(Attempt a, Settings s) {
        Instant end = closesAt(s);
        if (s.quizMinutes() != null) {
            Instant timer = a.startedAt().plusSeconds(s.quizMinutes() * 60L);
            end = end == null || timer.isBefore(end) ? timer : end;
        }
        return end;
    }

    /** Null means "no hard close": late work is allowed indefinitely. */
    public static Instant closesAt(Settings s) {
        return switch (s.latePolicy()) {
            case "BLOCK" -> s.deadline();
            default -> s.lateUntil();
        };
    }

    /** Starts a new attempt, or returns the one already in progress. Students only; the caller checks they can see the assignment. */
    @Transactional
    public Attempt start(long assignmentId, long studentId, Instant now) {
        Settings s = settings(assignmentId);
        if (!s.kind().equals("QUIZ")) throw ApiException.bad("This assignment is answered by submitting code");
        var open = jdbc.sql("SELECT id FROM quiz_attempts WHERE assignment_id=? AND student_id=? AND status='IN_PROGRESS'").params(assignmentId, studentId).query(Long.class).optional();
        if (open.isPresent()) {
            Attempt a = attempt(open.get());
            if (expired(a, s, now)) submit(a.id(), now);
            else return a;
        }
        if (s.availableFrom() != null && now.isBefore(s.availableFrom())) throw ApiException.conflict("This assessment opens on " + s.availableFrom());
        Instant close = closesAt(s);
        if (close != null && now.isAfter(close)) throw ApiException.conflict("This assessment is closed");
        int used = jdbc.sql("SELECT count(*) FROM quiz_attempts WHERE assignment_id=? AND student_id=?").params(assignmentId, studentId).query(Integer.class).single();
        if (used >= s.maxAttempts()) throw ApiException.conflict("You have used all " + s.maxAttempts() + " attempt" + (s.maxAttempts() == 1 ? "" : "s"));
        List<Long> ids = jdbc.sql("SELECT id FROM questions WHERE assignment_id=? ORDER BY ord, id").param(assignmentId).query(Long.class).list();
        if (ids.isEmpty()) throw ApiException.conflict("This assessment has no questions yet");
        if (s.shuffleQuestions()) Collections.shuffle(ids, new Random(studentId * 7919L + used));
        long id = jdbc.sql("INSERT INTO quiz_attempts(assignment_id, student_id, attempt, question_order, late) VALUES (?,?,?,?::jsonb,?) RETURNING id")
                .params(assignmentId, studentId, used + 1, json(om.valueToTree(ids), "[]"), now.isAfter(s.deadline())).query(Long.class).single();
        return attempt(id);
    }

    public boolean expired(Attempt a, Settings s, Instant now) {
        Instant end = endsAt(a, s);
        return a.status().equals("IN_PROGRESS") && end != null && now.isAfter(end.plusSeconds(GRACE_SECONDS));
    }

    @Transactional
    public void save(long attemptId, Map<Long, JsonNode> answers, Instant now) {
        Attempt a = attempt(attemptId);
        if (!a.status().equals("IN_PROGRESS")) throw ApiException.conflict("This attempt has already been submitted");
        Settings s = settings(a.assignmentId());
        if (expired(a, s, now)) {
            submit(attemptId, now);
            throw ApiException.conflict("Time is up. Your saved answers were submitted.");
        }
        Set<Long> valid = new HashSet<>(a.order());
        for (var e : answers.entrySet()) {
            if (!valid.contains(e.getKey())) throw ApiException.bad("Unknown question " + e.getKey());
            String body = json(e.getValue(), "{}");
            if (body.length() > 100_000) throw ApiException.bad("An answer is too long");
            jdbc.sql("""
                    INSERT INTO question_responses(attempt_id, question_id, response) VALUES (?,?,?::jsonb)
                    ON CONFLICT (attempt_id, question_id) DO UPDATE SET response=EXCLUDED.response, updated_at=now()""").params(attemptId, e.getKey(), body).update();
        }
    }

    /** Submits, marks every objective question and works out whether a person still has to mark anything. */
    @Transactional
    public Attempt submit(long attemptId, Instant now) {
        Attempt a = attempt(attemptId);
        if (!a.status().equals("IN_PROGRESS")) throw ApiException.conflict("This attempt has already been submitted");
        Settings s = settings(a.assignmentId());
        Instant end = endsAt(a, s);
        // a late arrival after a timer ran out is recorded at the moment time ran out, not when the browser came back
        Instant at = end != null && now.isAfter(end) && s.quizMinutes() != null ? end : now;
        jdbc.sql("UPDATE quiz_attempts SET status='SUBMITTED', submitted_at=?, late=? WHERE id=?").params(Timestamp.from(at), at.isAfter(s.deadline()), attemptId).update();
        queueCode(attemptId);
        mark(attemptId);
        return attempt(attemptId);
    }

    /** Recomputes automatic scores and the total for one attempt. Manual marks are kept. */
    public void mark(long attemptId) {
        Attempt a = attempt(attemptId);
        if (a.status().equals("IN_PROGRESS")) return;
        Settings s = settings(a.assignmentId());
        Map<Long, Question> qs = new HashMap<>();
        for (Question q : questions(a.assignmentId())) qs.put(q.id(), q);
        Map<Long, Response> rs = new HashMap<>();
        for (Response r : responses(attemptId)) rs.put(r.questionId(), r);
        Map<Long, int[]> judged = new HashMap<>();
        jdbc.sql("SELECT question_id, (judge_result->>'passed')::int, (judge_result->>'total')::int FROM question_responses WHERE attempt_id=? AND judge_status='DONE'").param(attemptId)
                .query((x, n) -> judged.put(x.getLong(1), new int[]{x.getInt(2), x.getInt(3)})).list();
        BigDecimal total = BigDecimal.ZERO;
        boolean pending = false;
        for (Question q : qs.values()) {
            Response r = rs.get(q.id());
            JsonNode resp = r == null ? om.createObjectNode() : r.response();
            BigDecimal auto = quizGrader.grade(q.type(), q.marks(), q.answer(), q.settings(), resp);
            if (q.type().equals("CODE") && judged.containsKey(q.id())) {
                int[] pt = judged.get(q.id());
                auto = pt[1] == 0 ? BigDecimal.ZERO.setScale(2) : q.marks().multiply(BigDecimal.valueOf(pt[0])).divide(BigDecimal.valueOf(pt[1]), 2, RoundingMode.HALF_UP);
            }
            boolean blank = resp == null || resp.isEmpty() || (q.type().equals("CODE") && resp.path("code").asText("").isBlank());
            if (auto == null && blank) auto = BigDecimal.ZERO.setScale(2); // nothing to read: zero, not "waiting for a marker"
            jdbc.sql("""
                    INSERT INTO question_responses(attempt_id, question_id, auto_score) VALUES (?,?,?)
                    ON CONFLICT (attempt_id, question_id) DO UPDATE SET auto_score=EXCLUDED.auto_score""").params(attemptId, q.id(), auto).update();
            BigDecimal manual = r == null ? null : r.manualScore();
            BigDecimal got = manual != null ? manual : auto;
            if (got == null) pending = true;
            else total = total.add(got);
        }
        if (total.signum() < 0) total = BigDecimal.ZERO;
        if (a.late() && s.latePolicy().equals("PENALTY") && s.latePenaltyPct() > 0)
            total = total.multiply(BigDecimal.valueOf(100 - s.latePenaltyPct())).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        String status = a.status().equals("RETURNED") ? "RETURNED" : pending ? "SUBMITTED" : "GRADED";
        boolean released = a.released() || (!pending && s.releaseMode().equals("IMMEDIATE"));
        jdbc.sql("UPDATE quiz_attempts SET score=?, status=?, released=? WHERE id=?").params(total.setScale(2, RoundingMode.HALF_UP), status, released, attemptId).update();
    }

    /** Code answers are judged by {@link QuizCodeMarker}; until then the question counts as waiting for marks. */
    void queueCode(long attemptId) {
        jdbc.sql("""
                UPDATE question_responses r SET judge_status='QUEUED', judge_result=NULL, updated_at=now() FROM questions q
                WHERE r.attempt_id=? AND q.id=r.question_id AND q.type='CODE' AND coalesce(trim(r.response->>'code'), '') <> ''""").param(attemptId).update();
    }

    public int regrade(long assignmentId) {
        List<Long> ids = jdbc.sql("SELECT id FROM quiz_attempts WHERE assignment_id=? AND status<>'IN_PROGRESS'").param(assignmentId).query(Long.class).list();
        ids.forEach(this::queueCode);
        ids.forEach(this::mark);
        return ids.size();
    }

    public record ManualMark(long questionId, BigDecimal score, String feedback) {
    }

    @Transactional
    public Attempt grade(long attemptId, List<ManualMark> marks, String feedback, String privateNote, Boolean release, boolean returnForRevision) {
        Attempt a = attempt(attemptId);
        if (a.status().equals("IN_PROGRESS")) throw ApiException.conflict("The student has not submitted this attempt yet");
        Map<Long, Question> qs = new HashMap<>();
        for (Question q : questions(a.assignmentId())) qs.put(q.id(), q);
        for (ManualMark m : marks) {
            Question q = qs.get(m.questionId());
            if (q == null) throw ApiException.bad("Unknown question " + m.questionId());
            if (m.score() != null && (m.score().compareTo(q.marks().negate()) < 0 || m.score().compareTo(q.marks()) > 0))
                throw ApiException.bad("Marks for a question must be between -" + q.marks().stripTrailingZeros().toPlainString() + " and " + q.marks().stripTrailingZeros().toPlainString());
            jdbc.sql("""
                    INSERT INTO question_responses(attempt_id, question_id, manual_score, feedback) VALUES (?,?,?,?)
                    ON CONFLICT (attempt_id, question_id) DO UPDATE SET manual_score=EXCLUDED.manual_score, feedback=EXCLUDED.feedback""")
                    .params(attemptId, q.id(), m.score(), m.feedback() == null ? "" : m.feedback()).update();
        }
        jdbc.sql("UPDATE quiz_attempts SET feedback=?, private_note=?, status=CASE WHEN ? THEN 'RETURNED' WHEN status='RETURNED' THEN 'SUBMITTED' ELSE status END WHERE id=?")
                .params(feedback == null ? a.feedback() : feedback, privateNote == null ? a.privateNote() : privateNote, returnForRevision, attemptId).update();
        if (release != null) jdbc.sql("UPDATE quiz_attempts SET released=? WHERE id=?").params(release, attemptId).update();
        mark(attemptId);
        return attempt(attemptId);
    }

    /** Whether the student may see their score and feedback now. */
    public boolean visibleToStudent(Attempt a, Settings s, Instant now) {
        if (a.status().equals("IN_PROGRESS")) return false;
        if (a.released()) return true;
        return s.releaseMode().equals("AFTER_DUE") && now.isAfter(s.deadline()) && !a.status().equals("SUBMITTED");
    }

    /** Option order a student sees, stable per attempt so a reload does not reshuffle. */
    public static List<Integer> optionOrder(long attemptId, long questionId, int n, boolean shuffle) {
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < n; i++) idx.add(i);
        if (shuffle) Collections.shuffle(idx, new Random(attemptId * 1_000_003L + questionId));
        return idx;
    }

    /** The question as a student sees it: no answer key, no explanation, options in attempt order with their original index. */
    public ObjectNode studentView(Question q, long attemptId, boolean shuffleOptions) {
        ObjectNode o = om.createObjectNode();
        o.put("id", q.id()).put("type", q.type()).put("prompt", q.prompt()).put("marks", q.marks()).put("difficulty", q.difficulty()).put("required", q.required());
        ArrayNode opts = o.putArray("options");
        if (q.options().isArray()) for (int i : optionOrder(attemptId, q.id(), q.options().size(), shuffleOptions)) opts.addObject().put("index", i).put("text", q.options().get(i).asText());
        ObjectNode st = o.putObject("settings");
        for (String k : List.of("code", "codeLanguage", "minWords", "maxWords", "acceptedFiles", "language", "starter")) if (q.settings().has(k)) st.set(k, q.settings().get(k));
        if (q.type().equals("CODE")) {
            ArrayNode samples = st.putArray("samples");
            int hidden = 0;
            for (JsonNode t : q.answer().path("tests")) {
                if (t.path("sample").asBoolean(false)) samples.addObject().put("input", t.path("input").asText("")).put("expected", t.path("expected").asText(""));
                else hidden++;
            }
            st.put("hidden", hidden);
        }
        if (q.type().equals("FILL_BLANK")) st.put("blanks", q.prompt().split("___", -1).length - 1);
        return o;
    }
}
