package dev.codesentinel.judge.sandbox;

/** Raw outcome of one container run, before any verdict is decided. */
public record RunResult(int exitCode, String stdout, String stderr, boolean timedOut, boolean oomKilled,
                        boolean outputLimitExceeded, long wallMillis) {
}
