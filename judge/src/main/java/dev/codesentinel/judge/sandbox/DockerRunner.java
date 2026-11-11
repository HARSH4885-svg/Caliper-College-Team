package dev.codesentinel.judge.sandbox;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs untrusted code in an ephemeral Docker container through the docker CLI (argument list, never a shell string).
 *
 * <p>Restrictions applied to every container (NFR-SEC-03): no network, non-root user, all capabilities dropped,
 * no-new-privileges, read-only root filesystem, a small non-persistent tmpfs, CPU/memory/swap/PID limits, file-size and
 * file-descriptor ulimits, bounded stdout/stderr, a host-enforced wall-clock timeout and guaranteed removal.
 * The Docker socket, the host home directory and the project directory are never mounted.
 *
 * <p><b>Residual risk:</b> containers share the host kernel; this is not a complete boundary against kernel/runtime
 * escapes, and whoever can talk to the Docker daemon is powerful. Do not expose this service publicly.
 */
public final class DockerRunner implements SandboxExecutionProvider {

    private static final int USER_ID = 65534; // nobody

    private final String docker;
    private final String runtime;

    public DockerRunner(String dockerBinary) {
        this(dockerBinary, null);
    }

    public DockerRunner(String dockerBinary, String runtime) {
        this.docker = dockerBinary == null || dockerBinary.isBlank() ? "docker" : dockerBinary;
        this.runtime = runtime == null || runtime.isBlank() ? null : runtime.trim();
    }

    @Override
    public String name() {
        return runtime != null && runtime.equalsIgnoreCase("runsc") ? "gvisor" : "docker";
    }

    @Override
    public java.util.Set<String> capabilities() {
        if ("gvisor".equals(name())) {
            return java.util.Set.of("gvisor_runsc", "kernel_virtualization", "network_isolation", "read_only_root", "non_root_user", "tmpfs");
        }
        return java.util.Set.of("cgroups", "network_isolation", "read_only_root", "non_root_user", "tmpfs");
    }

    @Override
    public boolean available() {
        try {
            Process p = new ProcessBuilder(docker, "version", "--format", "{{.Server.Version}}").redirectErrorStream(true).start();
            p.getOutputStream().close();
            if (!p.waitFor(10, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return false;
            }
            return p.exitValue() == 0;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return false;
        }
    }

    @Override
    public boolean imageExists(String image) {
        try {
            Process p = new ProcessBuilder(docker, "image", "inspect", "--format", "{{.Id}}", image).redirectErrorStream(true).start();
            p.getOutputStream().close();
            p.getInputStream().readAllBytes();
            return p.waitFor(15, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return false;
        }
    }

    /** Formats a volume mount path safely across Windows and Linux. */
    public static String formatVolumeMount(java.nio.file.Path hostDir, boolean writable) {
        String pathStr = hostDir.toAbsolutePath().normalize().toString();
        // Convert Windows backslashes to forward slashes for Docker daemon compatibility
        pathStr = pathStr.replace('\\', '/');
        String mode = writable ? "rw" : "ro";
        // SELinux ',Z' flag is valid only on Linux; on Windows Docker Desktop it triggers volume mount rejection
        String selinux = isLinux() ? ",Z" : "";
        return pathStr + ":/work:" + mode + selinux;
    }

    public static boolean isLinux() {
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        return os.contains("linux");
    }

    public static boolean isWindows() {
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        return os.contains("win");
    }

    List<String> buildCommand(ContainerSpec s, String name) {
        List<String> c = new ArrayList<>(List.of(docker, "run", "--name", name, "-i"));
        if (runtime != null) {
            c.add("--runtime");
            c.add(runtime);
        }
        c.addAll(List.of(
                "--network", "none",
                "--read-only",
                "--tmpfs", "/tmp:rw,noexec,nosuid,size=" + s.tmpfsMb() + "m",
                "--cap-drop", "ALL",
                "--security-opt", "no-new-privileges",
                "--pids-limit", String.valueOf(s.pidsLimit()),
                "--memory", s.memoryMb() + "m",
                "--memory-swap", s.memoryMb() + "m",
                "--cpus", String.valueOf(s.cpus()),
                "--ulimit", "nofile=256:256",
                "--ulimit", "core=0",
                "--ulimit", "fsize=" + Math.max(s.outputLimitBytes(), 1L << 20),
                "--user", USER_ID + ":" + USER_ID,
                "-v", formatVolumeMount(s.hostDir(), s.hostDirWritable()),
                "-w", "/work"));
        for (Map.Entry<String, String> e : s.env().entrySet()) {
            c.add("-e");
            c.add(e.getKey() + "=" + e.getValue());
        }
        c.add(s.image());
        c.addAll(s.command());
        return c;
    }

    @Override
    public RunResult run(ContainerSpec spec) throws SandboxException {
        String name = "cs-run-" + UUID.randomUUID().toString().substring(0, 12);
        long t0 = System.nanoTime();
        Process process;
        try {
            process = new ProcessBuilder(buildCommand(spec, name)).start();
        } catch (IOException e) {
            throw new SandboxException("cannot start docker: " + e.getMessage(), e);
        }
        AtomicBoolean limitHit = new AtomicBoolean();
        BoundedSink out = new BoundedSink(spec.outputLimitBytes(), limitHit);
        BoundedSink err = new BoundedSink(spec.outputLimitBytes(), limitHit);
        Thread tOut = pump(process.getInputStream(), out, "cs-out");
        Thread tErr = pump(process.getErrorStream(), err, "cs-err");
        Thread tIn = new Thread(() -> {
            try (OutputStream os = process.getOutputStream()) {
                if (spec.stdin() != null) os.write(spec.stdin());
            } catch (IOException ignored) {
                // the program may exit without reading all of its input
            }
        }, "cs-in");
        tIn.setDaemon(true);
        tIn.start();

        boolean timedOut = false;
        try {
            long deadline = System.nanoTime() + spec.wallTimeoutMs() * 1_000_000L;
            while (true) {
                if (process.waitFor(50, TimeUnit.MILLISECONDS)) break;
                if (limitHit.get()) {
                    kill(name);
                    process.waitFor(5, TimeUnit.SECONDS);
                    break;
                }
                if (System.nanoTime() > deadline) {
                    timedOut = true;
                    kill(name);
                    process.waitFor(5, TimeUnit.SECONDS);
                    break;
                }
            }
            tOut.join(2000);
            tErr.join(2000);
            if (process.isAlive()) process.destroyForcibly();
            boolean oom = false;
            int exit = process.isAlive() ? -1 : process.exitValue();
            String[] st = inspect(name);
            if (st != null) {
                oom = st[0].equals("true");
                try {
                    exit = Integer.parseInt(st[1]);
                } catch (NumberFormatException ignored) {
                    // keep the CLI's exit code
                }
            }
            if (st == null && exit != 0 && !timedOut && !limitHit.get()) {
                // The container was never created: this is a Docker problem (image, mount, daemon, permissions), not the
                // student's code, so it must not be reported as a compile error or a wrong answer.
                String why = err.text().isBlank() ? out.text() : err.text();
                throw new SandboxException("Docker could not start the sandbox container (exit " + exit + "): "
                        + (why.length() > 600 ? why.substring(0, 600) : why).trim());
            }
            long ms = (System.nanoTime() - t0) / 1_000_000;
            return new RunResult(exit, out.text(), err.text(), timedOut, oom, limitHit.get(), ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            kill(name);
            throw new SandboxException("interrupted", e);
        } finally {
            remove(name); // guaranteed disposal, even after timeouts and crashes
        }
    }

    private String[] inspect(String name) {
        try {
            Process p = new ProcessBuilder(docker, "inspect", "--format", "{{.State.OOMKilled}} {{.State.ExitCode}}", name)
                    .redirectErrorStream(true).start();
            p.getOutputStream().close();
            String s = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (!p.waitFor(15, TimeUnit.SECONDS) || p.exitValue() != 0) return null;
            String[] parts = s.split("\\s+");
            return parts.length == 2 ? parts : null;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return null;
        }
    }

    private void kill(String name) {
        quiet("kill", name);
    }

    private void remove(String name) {
        quiet("rm", "-f", name);
    }

    private void quiet(String... args) {
        try {
            List<String> cmd = new ArrayList<>();
            cmd.add(docker);
            cmd.addAll(List.of(args));
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            p.getOutputStream().close();
            p.getInputStream().readAllBytes();
            p.waitFor(20, TimeUnit.SECONDS);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
        }
    }

    private static Thread pump(InputStream in, BoundedSink sink, String name) {
        Thread t = new Thread(() -> {
            byte[] buf = new byte[8192];
            try (in) {
                int n;
                while ((n = in.read(buf)) >= 0) sink.write(buf, n);
            } catch (IOException ignored) {
                // stream closed by kill
            }
        }, name);
        t.setDaemon(true);
        t.start();
        return t;
    }

    /** Collects at most {@code limit} bytes; extra output is dropped and flagged so the container gets killed. */
    private static final class BoundedSink {
        private final long limit;
        private final AtomicBoolean flag;
        private final ByteArrayOutputStream buf = new ByteArrayOutputStream();

        BoundedSink(long limit, AtomicBoolean flag) {
            this.limit = limit;
            this.flag = flag;
        }

        synchronized void write(byte[] b, int n) {
            long room = limit - buf.size();
            if (room > 0) buf.write(b, 0, (int) Math.min(room, n));
            if (n > room) flag.set(true);
        }

        synchronized String text() {
            return buf.toString(StandardCharsets.UTF_8);
        }
    }
}
