package dev.codesentinel.judge.compiler;

import java.util.List;

/**
 * @param compileCommand argv run inside the language container with the work dir writable (null if none)
 * @param runCommand     argv run per testcase with the work dir read-only
 */
public record BuildPlan(List<String> compileCommand, List<String> runCommand) {
}
