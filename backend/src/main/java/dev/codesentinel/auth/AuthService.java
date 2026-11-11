package dev.codesentinel.auth;

import dev.codesentinel.common.ApiException;
import dev.codesentinel.common.AppProperties;
import dev.codesentinel.common.AuditService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {
    static final int MAX_FAILURES = 5;
    static final Duration WINDOW = Duration.ofMinutes(5);
    private static final String DUMMY_HASH = "$2a$10$7EqJtq98hPqEX7fNZaFWoOa4xN5E5qZ1dQ7s0x9bV0w3Yh1m0P5Hq";

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final AuditService audit;
    private final AppProperties props;
    private final JdbcClient jdbc;
    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt, AuditService audit, AppProperties props, JdbcClient jdbc) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.audit = audit;
        this.props = props;
        this.jdbc = jdbc;
    }

    public record AuthResponse(String token, Principal user) {
    }

    private boolean locked(String key) {
        Deque<Instant> d = failures.get(key);
        if (d == null) return false;
        synchronized (d) {
            Instant cut = Instant.now().minus(WINDOW);
            while (!d.isEmpty() && d.peekFirst().isBefore(cut)) d.pollFirst();
            return d.size() >= MAX_FAILURES;
        }
    }

    private void fail(String key) {
        Deque<Instant> d = failures.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (d) {
            d.addLast(Instant.now());
        }
    }

    public AuthResponse login(String email, String password) {
        String key = email.trim().toLowerCase();
        if (locked(key)) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too many failed attempts. Try again in a few minutes.");
        var row = users.findByEmail(email);
        boolean ok = encoder.matches(password, row.map(UserRepository.UserRow::passwordHash).orElse(DUMMY_HASH)) && row.isPresent() && row.get().enabled();
        if (!ok) {
            fail(key);
            audit.log(row.map(UserRepository.UserRow::id).orElse(null), "LOGIN_FAILED", "user", null, null);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }
        failures.remove(key);
        Principal p = row.get().principal();
        audit.log(p.id(), "LOGIN", "user", p.id(), null);
        return new AuthResponse(jwt.issue(p), p);
    }

    public AuthResponse register(String email, String name, String password, String enrollmentCode) {
        if (!props.registrationEnabled()) throw new ApiException(HttpStatus.FORBIDDEN, "Registration is disabled");
        var course = jdbc.sql("SELECT id FROM courses WHERE enrollment_code=?").param(enrollmentCode.trim()).query(Long.class).optional();
        if (course.isEmpty()) throw ApiException.bad("Invalid enrollment code");
        if (users.findByEmail(email).isPresent()) throw ApiException.conflict("An account with this email already exists");
        long id = users.insert(email, name, encoder.encode(password), "STUDENT", false);
        jdbc.sql("INSERT INTO enrollments(course_id, student_id) VALUES (?,?)").params(course.get(), id).update();
        audit.log(id, "REGISTER", "user", id, null);
        Principal p = new Principal(id, email.trim(), name.trim(), "STUDENT");
        return new AuthResponse(jwt.issue(p), p);
    }
}
