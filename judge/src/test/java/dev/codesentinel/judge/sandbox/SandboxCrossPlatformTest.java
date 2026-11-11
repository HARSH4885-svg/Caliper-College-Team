package dev.codesentinel.judge.sandbox;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SandboxCrossPlatformTest {

    @Test
    void formatVolumeMountConvertsBackslashesToForwardSlashes() {
        Path winPath = Path.of("C:\\Users\\runner\\AppData\\Local\\Temp\\work");
        String formatted = DockerRunner.formatVolumeMount(winPath, true);
        assertFalse(formatted.contains("\\"), "Formatted volume mount must not contain backslashes: " + formatted);
        assertTrue(formatted.contains(":/work:rw"), "Volume mount must map to /work:rw: " + formatted);
    }

    @Test
    void formatVolumeMountHandlesReadOnlyMode() {
        Path dir = Path.of("/tmp/codesentinel-judge/job-1234");
        String formatted = DockerRunner.formatVolumeMount(dir, false);
        assertTrue(formatted.contains(":/work:ro"), "Read-only mount must map to /work:ro: " + formatted);
        if (DockerRunner.isLinux()) {
            assertTrue(formatted.endsWith(",Z"), "Linux mount must include SELinux relabel flag: " + formatted);
        } else {
            assertFalse(formatted.contains(",Z"), "Non-Linux mount must omit SELinux ,Z flag: " + formatted);
        }
    }

    @Test
    void buildCommandIncludesRuntimeWhenConfigured() {
        DockerRunner gvisorRunner = new DockerRunner("docker", "runsc");
        ContainerSpec spec = new ContainerSpec("codesentinel/runner-python:1", List.of("python3", "main.py"),
                new byte[0], Path.of("/tmp/test"), false, 256, 1.0, 64, 5000, 65536, 16, java.util.Map.of());

        List<String> cmd = gvisorRunner.buildCommand(spec, "cs-test-1");
        int runtimeIndex = cmd.indexOf("--runtime");
        assertTrue(runtimeIndex >= 0, "Command must contain --runtime flag: " + cmd);
        assertEquals("runsc", cmd.get(runtimeIndex + 1), "Runtime must be runsc: " + cmd);
    }

    @Test
    void buildCommandOmitsRuntimeByDefault() {
        DockerRunner standardRunner = new DockerRunner("docker");
        ContainerSpec spec = new ContainerSpec("codesentinel/runner-python:1", List.of("python3", "main.py"),
                new byte[0], Path.of("/tmp/test"), false, 256, 1.0, 64, 5000, 65536, 16, java.util.Map.of());

        List<String> cmd = standardRunner.buildCommand(spec, "cs-test-2");
        assertFalse(cmd.contains("--runtime"), "Standard command must not contain --runtime flag: " + cmd);
    }

    @Test
    void sandboxProviderRegistryResolvesProviders() {
        SandboxProviderRegistry registry = new SandboxProviderRegistry("docker", "docker");
        assertEquals("docker", registry.activeRuntimeName());
        assertNotNull(registry.getActiveProvider());
        assertEquals("docker", registry.getActiveProvider().name());

        SandboxExecutionProvider gvisor = registry.getProvider("gvisor");
        assertNotNull(gvisor);
        assertEquals("gvisor", gvisor.name());
        assertTrue(gvisor.capabilities().contains("gvisor_runsc"));
    }

    @Test
    void capabilityDetectorInspectsHostWithoutCrashing() {
        SandboxCapabilityDetector detector = new SandboxCapabilityDetector("docker");
        SandboxCapabilityDetector.Diagnostic diag = detector.inspect();
        assertNotNull(diag.osName());
        assertNotNull(diag.osArch());
    }
}
