package dev.codesentinel.judge.sandbox;

import java.util.Set;

/**
 * Standard Linux container execution provider using Docker Engine / runc runtime.
 * Implements standard cgroups v2, namespace isolation, dropped capabilities, and non-root execution.
 */
public final class DockerSandboxProvider implements SandboxExecutionProvider {

    private final DockerRunner runner;

    public DockerSandboxProvider(String dockerBinary) {
        this.runner = new DockerRunner(dockerBinary, null);
    }

    public DockerSandboxProvider(DockerRunner runner) {
        this.runner = runner;
    }

    @Override
    public String name() {
        return "docker";
    }

    @Override
    public boolean available() {
        return runner.available();
    }

    @Override
    public boolean imageExists(String image) {
        return runner.imageExists(image);
    }

    @Override
    public RunResult run(ContainerSpec spec) throws SandboxException {
        return runner.run(spec);
    }

    @Override
    public Set<String> capabilities() {
        return Set.of("cgroups", "network_isolation", "read_only_root", "non_root_user", "tmpfs", "standard_linux_runc");
    }

    public DockerRunner runner() {
        return runner;
    }
}
