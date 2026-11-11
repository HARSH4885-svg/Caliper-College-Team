package dev.codesentinel.jobs;

import dev.codesentinel.common.AppProperties;
import dev.codesentinel.judge.Judge;
import dev.codesentinel.judge.JudgeRequest;
import dev.codesentinel.judge.testcase.TestCase;
import dev.codesentinel.judge.verdict.JudgeResult;
import dev.codesentinel.judge.verdict.TestCaseResult;
import dev.codesentinel.storage.FileStorageProvider;
import dev.codesentinel.submissions.LanguageFiles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Producer-consumer queue over PostgreSQL. The web app only inserts evaluation_jobs rows; this consumer claims them with
 * FOR UPDATE SKIP LOCKED, so several workers (or a separate "worker" profile process) can run safely. One job at a time per
 * worker bounds concurrent containers.
 */
@Component
@ConditionalOnProperty(name = "app.worker.enabled", havingValue = "true", matchIfMissing = true)
public class JobWorker {
    private static final Logger log = LoggerFactory.getLogger(JobWorker.class);
    private final JdbcClient jdbc;
    private final Judge judge;
    private final FileStorageProvider storage;
    private final AppProperties props;
    private final TransactionTemplate tx;

    public JobWorker(JdbcClient jdbc, Judge judge, FileStorageProvider storage, AppProperties props, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.judge = judge;
        this.storage = storage;
        this.props = props;
        this.tx = tx;
    }

    @Scheduled(fixedDelayString = "${app.worker.poll-millis:1500}", initialDelay = 1000)
    public void poll() {
        try {
            recoverStuck();
            while (processOne()) { /* drain */ }
        } catch (RuntimeException e) {
            log.error("Worker poll failed", e);
        }
    }

    public void recoverStuck() {
        if (jdbc == null) return;
        int max = props.worker().maxAttempts();
        jdbc.sql("UPDATE evaluation_jobs SET status='QUEUED' WHERE status='RUNNING' AND locked_at < now() - make_interval(mins => ?) AND attempts < ?")
                .params(props.worker().stuckMinutes(), max).update();
        jdbc.sql("UPDATE evaluation_jobs SET status='FAILED', error='Job exceeded its attempt limit', finished_at=now() WHERE status='RUNNING' AND locked_at < now() - make_interval(mins => ?) AND attempts >= ?")
                .params(props.worker().stuckMinutes(), max).update();
    }

    private record Claimed(long jobId, long submissionId, int attempts) {
    }

    /** @return true when a job was handled (so the caller keeps draining). */
    public boolean processOne() {
        Optional<Claimed> c = tx.execute(s -> jdbc.sql("""
                UPDATE evaluation_jobs SET status='RUNNING', locked_at=now(), attempts=attempts+1
                WHERE id = (SELECT id FROM evaluation_jobs WHERE status='QUEUED' ORDER BY id FOR UPDATE SKIP LOCKED LIMIT 1)
                RETURNING id, submission_id, attempts""").query((rs, n) -> new Claimed(rs.getLong(1), rs.getLong(2), rs.getInt(3))).optional());
        if (c == null || c.isEmpty()) return false;
        Claimed job = c.get();
        jdbc.sql("UPDATE submissions SET status='RUNNING' WHERE id=?").param(job.submissionId()).update();
        Path dir = null;
        try {
            var a = jdbc.sql("SELECT a.id, a.language, a.time_limit_ms, a.memory_limit_mb FROM submissions s JOIN assignments a ON a.id=s.assignment_id WHERE s.id=?").param(job.submissionId())
                    .query((rs, n) -> new Object[]{rs.getLong(1), rs.getString(2), rs.getInt(3), rs.getInt(4)}).single();
            String lang = (String) a[1];
            List<TestCase> tests = jdbc.sql("SELECT name, input, expected_output, sample FROM test_cases WHERE assignment_id=? ORDER BY ord, id").param(a[0])
                    .query((rs, n) -> new TestCase(rs.getString(1), rs.getString(2), rs.getString(3), rs.getBoolean(4))).list();
            dir = Files.createTempDirectory("cs-job-");
            var files = jdbc.sql("SELECT path, storage_key FROM submission_files WHERE submission_id=? ORDER BY path").param(job.submissionId())
                    .query((rs, n) -> new String[]{rs.getString(1), rs.getString(2)}).list();
            List<String> judged = new java.util.ArrayList<>();
            for (String[] f : files) {
                Path target = dir.resolve(f[0]).normalize();
                if (!target.startsWith(dir)) throw new IllegalStateException("bad stored path");
                Files.createDirectories(target.getParent());
                try (InputStream in = storage.read(f[1])) {
                    Files.copy(in, target);
                }
                if (LanguageFiles.sourceExtensions(lang).contains(LanguageFiles.ext(f[0]))) judged.add(f[0]);
            }
            JudgeResult r = tests.isEmpty() ? JudgeResult.internalError("Assignment has no test cases")
                    : judge.judge(new JudgeRequest("job-" + job.jobId(), lang, dir, judged, tests, (Integer) a[2], (Integer) a[3], 65536));
            save(job, r);
        } catch (Exception e) {
            log.error("Job {} failed", job.jobId(), e);
            if (job.attempts() < props.worker().maxAttempts()) {
                jdbc.sql("UPDATE evaluation_jobs SET status='QUEUED', error=? WHERE id=?").params(trunc(e.toString()), job.jobId()).update();
                jdbc.sql("UPDATE submissions SET status='QUEUED' WHERE id=?").param(job.submissionId()).update();
            } else {
                save(job, JudgeResult.internalError("Evaluation failed internally after " + job.attempts() + " attempts"));
                jdbc.sql("UPDATE evaluation_jobs SET status='FAILED', error=?, finished_at=now() WHERE id=?").params(trunc(e.toString()), job.jobId()).update();
                jdbc.sql("UPDATE submissions SET status='FAILED' WHERE id=?").param(job.submissionId()).update();
            }
        } finally {
            if (dir != null) deleteTree(dir);
        }
        return true;
    }

    private void save(Claimed job, JudgeResult r) {
        tx.executeWithoutResult(s -> {
            jdbc.sql("DELETE FROM evaluation_results WHERE submission_id=?").param(job.submissionId()).update();
            long rid = jdbc.sql("INSERT INTO evaluation_results(submission_id, verdict, passed, total, message, compile_log, millis) VALUES (?,?,?,?,?,?,?) RETURNING id")
                    .params(job.submissionId(), r.verdict().name(), r.passed(), r.total(), r.message(), r.compileLog(), r.totalMillis()).query(Long.class).single();
            for (TestCaseResult t : r.results()) {
                jdbc.sql("INSERT INTO testcase_results(result_id, name, sample, verdict, millis, detail, stdout_excerpt, stderr_excerpt) VALUES (?,?,?,?,?,?,?,?)")
                        .params(rid, t.name(), t.sample(), t.verdict().name(), t.millis(), t.detail(), t.stdoutExcerpt(), t.stderrExcerpt()).update();
            }
            jdbc.sql("UPDATE evaluation_jobs SET status='DONE', finished_at=now(), error=NULL WHERE id=?").param(job.jobId()).update();
            jdbc.sql("UPDATE submissions SET status='DONE' WHERE id=?").param(job.submissionId()).update();
        });
    }

    private static String trunc(String s) {
        return s.length() > 800 ? s.substring(0, 800) : s;
    }

    private static void deleteTree(Path root) {
        try (var w = Files.walk(root)) {
            w.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (Exception ignored) {
        }
    }
}
