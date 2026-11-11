package dev.codesentinel.judge.sandbox;

import java.util.Set;

/**
 * Pluggable execution provider interface for code evaluation sandboxes.
 * Supports standard Docker (runc), gVisor (runsc), and microVM-based architectures.
 */
public interface SandboxExecutionProvider extends ContainerRunner {
    @Override
    String name();

    @Override
    Set<String> capabilities();

    /** Detailed status report of this provider for administrator diagnostics. */
    default String statusReport() {
        return "Provider: " + name() + " (available: " + available() + ", capabilities: " + capabilities() + ")";
    }
}
