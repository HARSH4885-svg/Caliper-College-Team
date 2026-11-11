package dev.codesentinel.judge.sandbox;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Registry of available sandbox execution providers.
 * Manages provider resolution based on application configuration (CALIPER_SANDBOX_RUNTIME).
 */
public final class SandboxProviderRegistry {

    private final Map<String, SandboxExecutionProvider> providers = new LinkedHashMap<>();
    private final String activeRuntimeName;

    public SandboxProviderRegistry(String dockerBinary, String configuredRuntime) {
        this.activeRuntimeName = (configuredRuntime == null || configuredRuntime.isBlank())
                ? "docker"
                : configuredRuntime.trim().toLowerCase();

        DockerSandboxProvider dockerProvider = new DockerSandboxProvider(dockerBinary);
        GVisorSandboxProvider gvisorProvider = new GVisorSandboxProvider(dockerBinary);

        providers.put(dockerProvider.name(), dockerProvider);
        providers.put(gvisorProvider.name(), gvisorProvider);
    }

    public SandboxExecutionProvider getActiveProvider() {
        SandboxExecutionProvider provider = providers.get(activeRuntimeName);
        if (provider == null) {
            throw new IllegalArgumentException("Unknown sandbox runtime: '" + activeRuntimeName + "'. Supported options: " + providers.keySet());
        }
        return provider;
    }

    public SandboxExecutionProvider getProvider(String name) {
        if (name == null) return null;
        return providers.get(name.trim().toLowerCase());
    }

    public String activeRuntimeName() {
        return activeRuntimeName;
    }

    public Set<String> registeredProviderNames() {
        return Collections.unmodifiableSet(providers.keySet());
    }

    public Map<String, SandboxExecutionProvider> providers() {
        return Collections.unmodifiableMap(providers);
    }
}
