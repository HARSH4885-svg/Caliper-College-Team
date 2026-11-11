package dev.codesentinel.judge.compiler;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * Adapter pattern: hides compiler/runtime details behind one interface (section 16, 26). All commands are argv lists
 * executed INSIDE a disposable container - there is no host shell and no string interpolation of file names (names are
 * validated by {@link Files#validateName}).
 */
public interface LanguageAdapter {
    /** Language id used by assignments and the portal, e.g. "cpp". */
    String id();

    /** Container image (built from sandbox/Dockerfiles). */
    String image();

    Set<String> extensions();

    /**
     * @param files   safe relative paths below {@code srcDir}, all inside {@code /work/src} in the container
     * @param srcDir  host directory holding the files (read to detect entry points)
     * @param memoryMb the testcase memory limit (used to size the JVM heap)
     * @param timeLimitMs per-testcase CPU/wall limit
     */
    BuildPlan plan(List<String> files, Path srcDir, int memoryMb, long timeLimitMs) throws BuildException;
}
