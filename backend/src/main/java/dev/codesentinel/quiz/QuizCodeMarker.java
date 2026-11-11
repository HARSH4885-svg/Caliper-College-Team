package dev.codesentinel.quiz;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.codesentinel.judge.testcase.TestCase;
import dev.codesentinel.judge.verdict.JudgeResult;
import dev.codesentinel.run.CodeRunService;
import dev.codesentinel.run.RunController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;

/**
 * Judges code answers in submitted quiz attempts, one at a time, through the sandbox. The result keeps verdicts and timings
 * for every test but program output only for sample tests, so hidden-test behaviour never reaches a student.
 */
@Component
@ConditionalOnProperty(name = "app.worker.enabled", havingValue = "true", matchIfMissing = true)
public class QuizCodeMarker {
    private static final Logger log = LoggerFactory.getLogger(QuizCodeMarker.class);
    private final JdbcClient jdbc;
    private final QuizService quiz;
    private final CodeRunService runner;
    private final ObjectMapper om;
    private final TransactionTemplate tx;

    public QuizCodeMarker(JdbcClient jdbc, QuizService quiz, CodeRunService runner, ObjectMapper om, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.quiz = quiz;
        this.runner = runner;
        this.om = om;
        this.tx = tx;
    }

    @Scheduled(fixedDelayString = "${app.worker.poll-millis:1500}", initialDelay = 1500)
    public void poll() {
        try {
            jdbc.sql("UPDATE question_responses SET judge_status='QUEUED' WHERE judge_status='RUNNING' AND updated_at < now() - interval '10 minutes'").update();
            while (processOne()) { /* drain */ }
        } catch (RuntimeException e) {
            log.error("Quiz code marking failed", e);
        }
    }

    record Claim(long attemptId, long questionId, String code) {
    }

    public boolean processOne() {
        Optional<Claim> c = tx.execute(s -> jdbc.sql("""
                UPDATE question_responses SET judge_status='RUNNING', updated_at=now()
                WHERE (attempt_id, question_id) = (SELECT attempt_id, question_id FROM question_responses WHERE judge_status='QUEUED' ORDER BY updated_at LIMIT 1 FOR UPDATE SKIP LOCKED)
                RETURNING attempt_id, question_id, response->>'code'""").query((rs, n) -> new Claim(rs.getLong(1), rs.getLong(2), rs.getString(3))).optional());
        if (c == null || c.isEmpty()) return false;
        Claim cl = c.get();
        var a = quiz.attempt(cl.attemptId());
        var q = quiz.questions(a.assignmentId()).stream().filter(x -> x.id() == cl.questionId()).findFirst().orElse(null);
        ObjectNode out = om.createObjectNode();
        if (q == null) {
            out.put("verdict", "IE").put("passed", 0).put("total", 0).put("message", "The question no longer exists");
        } else {
            List<TestCase> tests = RunController.codeTests(q.answer());
            String lang = q.settings().path("language").asText("python");
            JudgeResult j;
            try {
                CodeRunService.validate(lang, cl.code(), null);
                j = runner.run(lang, cl.code(), tests, q.settings().path("timeLimitMs").asInt(2000), 256);
            } catch (RuntimeException e) {
                j = JudgeResult.internalError(e.getMessage());
            }
            out.put("verdict", j.verdict().name()).put("passed", j.passed()).put("total", j.total() == 0 ? tests.size() : j.total()).put("message", j.message())
                    .put("compileLog", j.compileLog() == null ? "" : j.compileLog().length() > 4000 ? j.compileLog().substring(0, 4000) : j.compileLog()).put("millis", j.totalMillis());
            ArrayNode arr = out.putArray("tests");
            for (int i = 0; i < j.results().size(); i++) {
                var t = j.results().get(i);
                ObjectNode n = arr.addObject().put("name", t.name()).put("sample", t.sample()).put("verdict", t.verdict().name()).put("millis", t.millis());
                if (t.sample()) n.put("actual", t.stdoutExcerpt()).put("stderr", t.stderrExcerpt()).put("detail", t.detail());
            }
            // a judge failure is not the student's fault: leave the answer waiting for a person instead of scoring zero
            if (j.verdict().name().equals("IE")) out.put("passed", 0);
        }
        boolean ie = "IE".equals(out.path("verdict").asText());
        jdbc.sql("UPDATE question_responses SET judge_status=?, judge_result=?::jsonb, updated_at=now() WHERE attempt_id=? AND question_id=?")
                .params(ie ? null : "DONE", out.toString(), cl.attemptId(), cl.questionId()).update();
        quiz.mark(cl.attemptId());
        return true;
    }
}
