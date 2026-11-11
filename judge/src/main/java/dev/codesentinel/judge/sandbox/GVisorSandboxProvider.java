package dev.codesentinel.judge.sandbox;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Advanced Linux container execution provider using Google gVisor (runsc) runtime.
 * Implements an application-level user-space kernel (Sentry and Gofer) that intercepts and virtualizes
 * all Linux system calls, providing strong defense-in-depth against host kernel exploits.
 */
public final class GVisorSandboxProvider implements SandboxExecutionProvider {

    private final String docker;
    private final DockerRunner runner;

    public GVisorSandboxProvider(String dockerBinary) {
        this.docker = dockerBinary == null || dockerBinary.isBlank() ? "docker" : dockerBinary;
        this.runner = new DockerRunner(this.docker, "runsc");
    }

    @Override
    public String name() {
        return "gvisor";
    }

    @Override
    public boolean available() {
        if (!runner.available()) {
            return false;
        }
        return isRunscRuntimeRegistered();
    }

    public boolean isRunscRuntimeRegistered() {
        try {
            Process p = new ProcessBuilder(docker, "info", "--format", "{{json .Runtimes}}")
                    .redirectErrorStream(true)
                    .start();
            p.getOutputStream().close();
            String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!p.waitFor(10, TimeUnit.SECONDS) || p.exitValue() != 0) {
                p.destroyForcibly();
                return false;
            }
            return output.toLowerCase().contains("runsc");
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return false;
        }
    }

    @Override
    public boolean imageExists(String image) {
        return runner.imageExists(image);
    }

    @Override
    public RunResult run(ContainerSpec spec) throws SandboxException {
        if (!available()) {
            throw new SandboxException("gVisor (runsc) runtime is requested but not available on this Docker host. " +
                    "Ensure 'runsc' is installed and configured in /etc/docker/daemon.json under 'runtimes'.");
        }
        return runner.run(spec);
    }

    @Override
    public Set<String> capabilities() {
        return Set.of(
                "gvisor_runsc",
                "application_kernel_virtualization",
                "seccomp_bpf_syscall_filtering",
                "network_isolation",
                "read_only_root",
                "non_root_user",
                "tmpfs"
        );
    }

    public DockerRunner runner() {
        return runner;
    }
}
