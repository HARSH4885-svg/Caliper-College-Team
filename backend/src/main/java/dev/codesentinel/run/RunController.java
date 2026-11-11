package dev.codesentinel.run;

import com.fasterxml.jackson.databind.JsonNode;
import dev.codesentinel.auth.CurrentUser;
import dev.codesentinel.auth.Principal;
import dev.codesentinel.common.Access;
import dev.codesentinel.common.ApiException;
import dev.codesentinel.common.AuditService;
import dev.codesentinel.judge.testcase.TestCase;
import dev.codesentinel.judge.verdict.JudgeResult;
import dev.codesentinel.quiz.QuizService;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * "Run" without submitting. Students run against sample tests or their own input; course staff may also run hidden tests
 * (to check a reference solution). Hidden test inputs and expected outputs are never returned to students.
 */
@RestController
@RequestMapping("/api")
public class RunController {
    record RunRequest(String source, String mode, String input, Integer test) {
    }

    public record RunTest(String name, boolean sample, String input, String expected, String actual, String stderr, String verdict, long millis, String detail) {
    }

    public record RunResponse(String verdict, String message, String compileLog, int passed, int total, long millis, String mode, List<RunTest> tests) {
    }

    private final CodeRunService runner;
    private final JdbcClient jdbc;
    private final Access access;
    private final QuizService quiz;
    private final AuditService audit;

    public RunController(CodeRunService runner, JdbcClient jdbc, Access access, QuizService quiz, AuditService audit) {
        this.runner = runner;
        this.jdbc = jdbc;
        this.access = access;
        this.quiz = quiz;
        this.audit = audit;
    }

    static List<TestCase> pick(List<TestCase> all, RunRequest r, boolean staff) {
        String mode = r.mode() == null ? "samples" : r.mode();
        switch (mode) {
            case "custom" -> {
                return List.of(new TestCase("Custom input", r.input() == null ? "" : r.input(), "", true));
            }
            case "all" -> {
                if (!staff) throw ApiException.forbidden();
                return all;
            }
            case "samples" -> {
                List<TestCase> s = all.stream().filter(TestCase::sample).toList();
                if (r.test() != null) {
                    if (r.test() < 0 || r.test() >= s.size()) throw ApiException.bad("No such sample test");
                    return List.of(s.get(r.test()));
                }
                if (s.isEmpty()) throw ApiException.bad("There are no sample tests; use custom input instead");
                return s;
            }
            default -> throw ApiException.bad("mode must be samples, custom or all");
        }
    }

    static RunResponse shape(JudgeResult j, List<TestCase> tests, boolean custom) {
        List<RunTest> out = new ArrayList<>();
        for (int i = 0; i < j.results().size(); i++) {
            var t = j.results().get(i);
            TestCase tc = tests.get(i);
            boolean show = tc.sample() || custom;
            String v = t.verdict().name();
            if (custom && (v.equals("AC") || v.equals("WA"))) v = "OK"; // nothing to compare with: it ran
            out.add(new RunTest(t.name(), tc.sample(), show ? tc.input() : null, custom ? null : (show ? tc.expectedOutput() : null), t.stdoutExcerpt(), t.stderrExcerpt(), v, t.millis(),
                    custom && v.equals("OK") ? null : t.detail()));
        }
        String verdict = j.verdict().name();
        if (custom && (verdict.equals("AC") || verdict.equals("WA"))) verdict = "OK";
        return new RunResponse(verdict, j.message(), j.compileLog(), custom ? 0 : j.passed(), custom ? 0 : j.total(), j.totalMillis(), custom ? "custom" : "tests", out);
    }

    @PostMapping("/assignments/{id}/run")
    RunResponse runAssignment(@PathVariable long id, @RequestBody RunRequest r) {
        Principal p = CurrentUser.get();
        boolean staff = p.instructor();
        if (staff) access.requireAssignmentOwner(id, p);
        else access.requireAssignmentVisible(id, p);
        var a = jdbc.sql("SELECT language, kind, time_limit_ms, memory_limit_mb FROM assignments WHERE id=?").param(id)
                .query((rs, n) -> new Object[]{rs.getString(1), rs.getString(2), rs.getInt(3), rs.getInt(4)}).single();
        if (!"CODING".equals(a[1])) throw ApiException.bad("This assignment has no code to run");
        CodeRunService.validate((String) a[0], r.source(), r.input());
        runner.checkLimits(p.id());
        List<TestCase> all = jdbc.sql("SELECT name, input, expected_output, sample FROM test_cases WHERE assignment_id=? ORDER BY ord, id").param(id)
                .query((rs, n) -> new TestCase(rs.getString(1), rs.getString(2), rs.getString(3), rs.getBoolean(4))).list();
        List<TestCase> tests = pick(all, r, staff);
        audit.log(p.id(), "CODE_RUN", "assignment", id, (r.mode() == null ? "samples" : r.mode()) + ", " + tests.size() + " tests");
        return shape(runner.run((String) a[0], r.source(), tests, (Integer) a[2], (Integer) a[3]), tests, "custom".equals(r.mode()));
    }

    /** A code question inside a quiz. The student runs it while their attempt is open; staff can run it from the builder. */
    @PostMapping("/questions/{questionId}/run")
    RunResponse runQuestion(@PathVariable long questionId, @RequestParam(required = false) Long attempt, @RequestBody RunRequest r) {
        Principal p = CurrentUser.get();
        long assignmentId = jdbc.sql("SELECT assignment_id FROM questions WHERE id=? AND type='CODE'").param(questionId).query(Long.class).optional().orElseThrow(ApiException::notFound);
        boolean staff = p.instructor();
        if (staff) access.requireAssignmentOwner(assignmentId, p);
        else {
            if (attempt == null) throw ApiException.notFound();
            var a = quiz.attempt(attempt);
            if (a.studentId() != p.id() || a.assignmentId() != assignmentId) throw ApiException.notFound();
            access.requireAssignmentVisible(assignmentId, p);
            if (!a.status().equals("IN_PROGRESS")) throw ApiException.conflict("This attempt has been submitted");
        }
        var q = quiz.questions(assignmentId).stream().filter(x -> x.id() == questionId).findFirst().orElseThrow(ApiException::notFound);
        String lang = q.settings().path("language").asText("python");
        CodeRunService.validate(lang, r.source(), r.input());
        runner.checkLimits(p.id());
        List<TestCase> tests = pick(codeTests(q.answer()), r, staff);
        return shape(runner.run(lang, r.source(), tests, q.settings().path("timeLimitMs").asInt(2000), 256), tests, "custom".equals(r.mode()));
    }

    public static List<TestCase> codeTests(JsonNode answer) {
        List<TestCase> t = new ArrayList<>();
        int i = 1;
        for (JsonNode n : answer.path("tests")) {
            boolean sample = n.path("sample").asBoolean(false);
            t.add(new TestCase(n.path("name").asText(sample ? "Sample " + i : "Hidden " + i), n.path("input").asText(""), n.path("expected").asText(""), sample));
            i++;
        }
        return t;
    }
}
