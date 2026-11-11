package dev.codesentinel.auth;

import dev.codesentinel.common.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

@Service
public class JwtService {
    private final SecretKey key;
    private final Duration ttl;

    public JwtService(AppProperties props) {
        String s = props.jwt().secret();
        if (s == null || s.length() < 32) throw new IllegalStateException("JWT_SECRET must be set to at least 32 characters");
        this.key = Keys.hmacShaKeyFor(s.getBytes(StandardCharsets.UTF_8));
        this.ttl = Duration.ofMinutes(props.jwt().ttlMinutes());
    }

    public String issue(Principal p) {
        Instant now = Instant.now();
        return Jwts.builder().subject(Long.toString(p.id())).claim("role", p.role()).claim("email", p.email()).claim("name", p.displayName())
                .issuedAt(Date.from(now)).expiration(Date.from(now.plus(ttl))).signWith(key).compact();
    }

    public Optional<Principal> parse(String token) {
        try {
            Claims c = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            return Optional.of(new Principal(Long.parseLong(c.getSubject()), c.get("email", String.class), c.get("name", String.class), c.get("role", String.class)));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
