package dev.codesentinel.judge.sandbox;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Detects host sandbox capabilities, Docker daemon runtime registrations, and hardware virtualization.
 */
public final class SandboxCapabilityDetector {

    private final String docker;

    public SandboxCapabilityDetector(String dockerBinary) {
        this.docker = dockerBinary == null || dockerBinary.isBlank() ? "docker" : dockerBinary;
    }

    public record Diagnostic(
            String osName,
            String osArch,
            boolean isLinux,
            boolean isWindows,
            boolean dockerAvailable,
            String dockerVersion,
            boolean gvisorSupported,
            boolean kvmAvailable,
            Map<String, String> registeredRuntimes
    ) {}

    public Diagnostic inspect() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "");
        boolean isLin = os.contains("linux");
        boolean isWin = os.contains("win");

        boolean dockerOk = false;
        String dockerVer = "unavailable";
        boolean gvisorOk = false;
        Map<String, String> runtimes = new LinkedHashMap<>();

        try {
            Process p = new ProcessBuilder(docker, "version", "--format", "{{.Server.Version}}")
                    .redirectErrorStream(true)
                    .start();
            p.getOutputStream().close();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0) {
                dockerOk = true;
                dockerVer = out;
            }
        } catch (Exception ignored) {
        }

        if (dockerOk) {
            try {
                Process p = new ProcessBuilder(docker, "info", "--format", "{{json .Runtimes}}")
                        .redirectErrorStream(true)
                        .start();
                p.getOutputStream().close();
                String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                if (p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0) {
                    if (out.toLowerCase().contains("runsc")) {
                        gvisorOk = true;
                        runtimes.put("runsc", "Google gVisor (Installed & Registered)");
                    }
                    if (out.toLowerCase().contains("runc")) {
                        runtimes.put("runc", "Standard Linux OCI runc");
                    }
                }
            } catch (Exception ignored) {
            }
        }

        boolean kvmOk = false;
        File kvm = new File("/dev/kvm");
        if (kvm.exists() && kvm.canRead() && kvm.canWrite()) {
            kvmOk = true;
        }

        return new Diagnostic(os, arch, isLin, isWin, dockerOk, dockerVer, gvisorOk, kvmOk, runtimes);
    }
}
