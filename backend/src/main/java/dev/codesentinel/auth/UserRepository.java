package dev.codesentinel.auth;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class UserRepository {
    public record UserRow(long id, String email, String displayName, String passwordHash, String role, boolean enabled) {
        Principal principal() {
            return new Principal(id, email, displayName, role);
        }
    }

    private final JdbcClient jdbc;

    public UserRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private static final String COLS = "id, email, display_name, password_hash, role, enabled";

    public Optional<UserRow> findByEmail(String email) {
        return jdbc.sql("SELECT " + COLS + " FROM users WHERE lower(email)=lower(?)").param(email)
                .query((rs, n) -> new UserRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getBoolean(6))).optional();
    }

    public Optional<UserRow> findById(long id) {
        return jdbc.sql("SELECT " + COLS + " FROM users WHERE id=?").param(id)
                .query((rs, n) -> new UserRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getBoolean(6))).optional();
    }

    public long insert(String email, String name, String hash, String role, boolean synthetic) {
        return jdbc.sql("INSERT INTO users(email, display_name, password_hash, role, synthetic) VALUES (?,?,?,?,?) RETURNING id")
                .params(email.trim(), name.trim(), hash, role, synthetic).query(Long.class).single();
    }

    public void updateDisplayName(long id, String displayName) {
        jdbc.sql("UPDATE users SET display_name=? WHERE id=?").params(displayName, id).update();
    }

    public void updatePasswordHash(long id, String passwordHash) {
        jdbc.sql("UPDATE users SET password_hash=? WHERE id=?").params(passwordHash, id).update();
    }
}
