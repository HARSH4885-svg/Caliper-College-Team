package dev.codesentinel.judge.sandbox;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Everything needed to run one disposable container. Only {@code hostDir} (a job-private directory holding the
 * submission) is mounted; stdin carries the testcase input so hidden data never exists inside the container's filesystem.
 */
public record ContainerSpec(String image, List<String> command, byte[] stdin, Path hostDir, boolean hostDirWritable,
                            int memoryMb, double cpus, int pidsLimit, long wallTimeoutMs, long outputLimitBytes,
                            int tmpfsMb, Map<String, String> env) {
}
