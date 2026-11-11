package dev.codesentinel.judge;

import dev.codesentinel.judge.testcase.TestCase;

import java.nio.file.Path;
import java.util.List;

/**
 * @param sourceRoot directory holding the submitted files (read only by the judge; copied into a job-private directory)
 * @param files      safe relative paths below {@code sourceRoot}
 */
public record JudgeRequest(String jobId, String language, Path sourceRoot, List<String> files, List<TestCase> tests,
                           int timeLimitMs, int memoryLimitMb, long outputLimitBytes) {
}
