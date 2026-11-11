package dev.codesentinel.judge;

import java.nio.file.Path;

/**
 * Operator-side limits. Assignment-level time/memory limits are clamped into [min, max] (admin bounds, section 25).
 */
public record JudgeConfig(Path workRoot, long compileTimeoutMs, int compileMemoryMb, long jobTimeoutMs, long startupAllowanceMs,
                          int pidsLimit, double cpus, int tmpfsMb, int minTimeMs, int maxTimeMs, int minMemoryMb, int maxMemoryMb,
                          int excerptBytes) {
    public static JudgeConfig defaults() {
        return new JudgeConfig(Path.of(System.getProperty("java.io.tmpdir"), "codesentinel-judge"),
                60_000, 768, 240_000, 4_000, 64, 1.0, 16, 100, 20_000, 16, 1024, 2048);
    }

    public int clampTime(int ms) {
        return Math.max(minTimeMs, Math.min(maxTimeMs, ms));
    }

    public int clampMemory(int mb) {
        return Math.max(minMemoryMb, Math.min(maxMemoryMb, mb));
    }
}
