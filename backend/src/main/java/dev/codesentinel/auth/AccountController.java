package dev.codesentinel.auth;

import dev.codesentinel.common.ApiException;
import dev.codesentinel.common.AuditService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Self-service account changes: display name and password. Role, email and enrolments are never editable here. */
@RestController
@RequestMapping("/api/auth")
public class AccountController {
    record ProfileRequest(@NotBlank @Size(max = 100) String displayName) {
    }

    record PasswordRequest(@NotBlank @Size(max = 200) String currentPassword, @NotBlank @Size(min = 8, max = 200) String newPassword) {
    }

    static final int MAX_WRONG = 5;
    static final Duration WINDOW = Duration.ofMinutes(10);

    private final JdbcClient jdbc;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final Map<Long, Deque<Instant>> wrong = new ConcurrentHashMap<>();

    public AccountController(JdbcClient jdbc, UserRepository users, PasswordEncoder encoder, AuditService audit) {
        this.jdbc = jdbc;
        this.users = users;
        this.encoder = encoder;
        this.audit = audit;
    }

    @PatchMapping("/me")
    Principal rename(@Valid @RequestBody ProfileRequest r) {
        Principal p = CurrentUser.get();
        String name = r.displayName().trim();
        if (name.isEmpty()) throw ApiException.bad("Name cannot be empty");
        users.updateDisplayName(p.id(), name);
        audit.log(p.id(), "PROFILE_UPDATE", "user", p.id(), null);
        return new Principal(p.id(), p.email(), name, p.role());
    }

    @PostMapping("/password")
    ResponseEntity<Void> changePassword(@Valid @RequestBody PasswordRequest r) {
        Principal p = CurrentUser.get();
        Deque<Instant> d = wrong.computeIfAbsent(p.id(), k -> new ArrayDeque<>());
        synchronized (d) {
            Instant cut = Instant.now().minus(WINDOW);
            while (!d.isEmpty() && d.peekFirst().isBefore(cut)) d.pollFirst();
            if (d.size() >= MAX_WRONG) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too many wrong passwords. Try again in a few minutes.");
        }
        var row = users.findById(p.id()).orElseThrow(ApiException::notFound);
        if (!encoder.matches(r.currentPassword(), row.passwordHash())) {
            synchronized (d) {
                d.addLast(Instant.now());
            }
            audit.log(p.id(), "PASSWORD_CHANGE_FAILED", "user", p.id(), null);
            throw ApiException.bad("Current password is incorrect");
        }
        if (r.currentPassword().equals(r.newPassword())) throw ApiException.bad("Choose a password different from the current one");
        users.updatePasswordHash(p.id(), encoder.encode(r.newPassword()));
        wrong.remove(p.id());
        audit.log(p.id(), "PASSWORD_CHANGE", "user", p.id(), null);
        return ResponseEntity.noContent().build();
    }
}
