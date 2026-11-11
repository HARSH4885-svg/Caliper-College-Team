package dev.codesentinel.run;

import dev.codesentinel.common.ApiException;
import dev.codesentinel.common.AppProperties;
import dev.codesentinel.common.RateLimiter;
import dev.codesentinel.judge.Judge;
import dev.codesentinel.judge.JudgeRequest;
import dev.codesentinel.judge.testcase.TestCase;
import dev.codesentinel.judge.verdict.JudgeResult;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Interactive runs ("Run code" in the workspace) through the same sandboxed judge as graded submissions: no network,
 * read-only root, CPU/memory/pid/time limits. Nothing is stored. A small semaphore bounds how many containers interactive
 * runs may start at once, so graded work in the queue is never starved, and a per-user limiter stops runaway clicking.
 */
@Service
public class CodeRunService {
    public static final Map<String, String> ENTRY = Map.of("python", "main.py", "java", "Main.java", "c", "main.c", "cpp", "main.cpp", "javascript", "main.js");
    static final int MAX_SOURCE = 256 * 1024;
    static final int MAX_INPUT = 64 * 1024;

    private final Judge judge;
    private final Semaphore slots;
    private final RateLimiter perUser;

    public CodeRunService(Judge judge, AppProperties props) {
        this.judge = judge;
        this.slots = new Semaphore(2, true);
        this.perUser = new RateLimiter(Math.max(0, props.rateLimitPerMinute()) * 2, 120_000);
    }

    public void checkLimits(long userId) {
        if (!perUser.allow("run:" + userId, System.currentTimeMillis()))
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "You are running code very often. Wait a minute and try again.");
    }

    public static void validate(String language, String source, String input) {
        if (!ENTRY.containsKey(language)) throw ApiException.bad("This language cannot be run");
        if (source == null || source.isBlank()) throw ApiException.bad("Write some code first");
        if (source.getBytes(StandardCharsets.UTF_8).length > MAX_SOURCE) throw ApiException.bad("The code is larger than 256 KiB");
        if (input != null && input.length() > MAX_INPUT) throw ApiException.bad("Custom input is limited to 64 KiB");
    }

    /** Runs {@code source} against {@code tests}. Blocks while waiting for a free sandbox slot (at most 20 s). */
    public JudgeResult run(String language, String source, List<TestCase> tests, int timeLimitMs, int memoryLimitMb) {
        boolean got;
        try {
            got = slots.tryAcquire(20, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Interrupted");
        }
        if (!got) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "The sandbox is busy. Try again in a few seconds.");
        Path dir = null;
        try {
            dir = Files.createTempDirectory("cs-run-");
            Files.writeString(dir.resolve(ENTRY.get(language)), source, StandardCharsets.UTF_8);
            return judge.judge(new JudgeRequest("run-" + UUID.randomUUID(), language, dir, List.of(ENTRY.get(language)), tests, timeLimitMs, memoryLimitMb, 65536));
        } catch (java.io.IOException e) {
            return JudgeResult.internalError("Could not prepare the run: " + e.getMessage());
        } finally {
            slots.release();
            if (dir != null) try (var w = Files.walk(dir)) {
                w.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            } catch (Exception ignored) {
            }
        }
    }
}
