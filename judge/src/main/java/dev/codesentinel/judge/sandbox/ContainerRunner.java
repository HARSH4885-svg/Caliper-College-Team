package dev.codesentinel.judge.sandbox;

import java.util.Set;

/** Port for running a container. Implementations provide container execution environments. */
public interface ContainerRunner {
    /** True if the container engine answers. If false the judge is unavailable and nothing is executed anywhere. */
    boolean available();

    /** True if the named image exists locally. */
    boolean imageExists(String image);

    RunResult run(ContainerSpec spec) throws SandboxException;

    /** Name of the execution provider (e.g. "docker", "gvisor"). */
    default String name() {
        return "docker";
    }

    /** Capabilities supported by this execution provider. */
    default Set<String> capabilities() {
        return Set.of("cgroups", "network_isolation", "read_only_root", "non_root_user", "tmpfs");
    }
}

