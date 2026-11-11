package dev.codesentinel.jobs;

import dev.codesentinel.auth.CurrentUser;
import dev.codesentinel.judge.sandbox.SandboxCapabilityDetector;
import dev.codesentinel.judge.sandbox.SandboxExecutionProvider;
import dev.codesentinel.judge.sandbox.SandboxProviderRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/**
 * Diagnostic and management endpoint for sandbox execution runtimes.
 * Allows instructors and administrators to inspect active isolation levels (runc, gVisor, microVM).
 */
@RestController
@RequestMapping("/api/admin/sandbox")
public class AdminSandboxController {

    private final SandboxProviderRegistry registry;
    private final SandboxCapabilityDetector detector;

    public AdminSandboxController(SandboxProviderRegistry registry, dev.codesentinel.common.AppProperties props) {
        this.registry = registry;
        this.detector = new SandboxCapabilityDetector(props.judge().dockerBin());
    }

    public record SandboxStatusResponse(
            String activeRuntime,
            boolean isAvailable,
            Set<String> capabilities,
            Set<String> supportedRuntimes,
            SandboxCapabilityDetector.Diagnostic systemDiagnostic
    ) {}

    @GetMapping("/status")
    public ResponseEntity<SandboxStatusResponse> status() {
        CurrentUser.instructor();
        SandboxExecutionProvider provider = registry.getActiveProvider();
        return ResponseEntity.ok(new SandboxStatusResponse(
                registry.activeRuntimeName(),
                provider.available(),
                provider.capabilities(),
                registry.registeredProviderNames(),
                detector.inspect()
        ));
    }
}
