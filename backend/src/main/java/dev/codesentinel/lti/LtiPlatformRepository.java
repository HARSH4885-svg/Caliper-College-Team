package dev.codesentinel.lti;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class LtiPlatformRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public LtiPlatformRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<LtiPlatform> findByIssuer(String issuer) {
        String sql = "SELECT * FROM lti_platforms WHERE issuer = :iss";
        List<LtiPlatform> list = jdbc.query(sql, new MapSqlParameterSource("iss", issuer), this::mapPlatform);
        return list.stream().findFirst();
    }

    public Optional<LtiPlatform> findById(long id) {
        String sql = "SELECT * FROM lti_platforms WHERE id = :id";
        List<LtiPlatform> list = jdbc.query(sql, new MapSqlParameterSource("id", id), this::mapPlatform);
        return list.stream().findFirst();
    }

    public List<LtiPlatform> findAll() {
        String sql = "SELECT * FROM lti_platforms ORDER BY id ASC";
        return jdbc.query(sql, this::mapPlatform);
    }

    public LtiPlatform save(LtiPlatform p) {
        if (p.id() == null) {
            String sql = """
                    INSERT INTO lti_platforms (name, issuer, client_id, deployment_id, auth_url, token_url, jwks_url)
                    VALUES (:name, :iss, :cid, :dep, :auth, :tok, :jwks)
                    RETURNING id
                    """;
            var params = new MapSqlParameterSource()
                    .addValue("name", p.name())
                    .addValue("iss", p.issuer())
                    .addValue("cid", p.clientId())
                    .addValue("dep", p.deploymentId())
                    .addValue("auth", p.authUrl())
                    .addValue("tok", p.tokenUrl())
                    .addValue("jwks", p.jwksUrl());
            GeneratedKeyHolder kh = new GeneratedKeyHolder();
            jdbc.update(sql, params, kh);
            long id = ((Number) kh.getKeys().get("id")).longValue();
            return new LtiPlatform(id, p.name(), p.issuer(), p.clientId(), p.deploymentId(), p.authUrl(), p.tokenUrl(), p.jwksUrl(), Instant.now());
        } else {
            String sql = """
                    UPDATE lti_platforms
                    SET name = :name, issuer = :iss, client_id = :cid, deployment_id = :dep,
                        auth_url = :auth, token_url = :tok, jwks_url = :jwks
                    WHERE id = :id
                    """;
            var params = new MapSqlParameterSource()
                    .addValue("id", p.id())
                    .addValue("name", p.name())
                    .addValue("iss", p.issuer())
                    .addValue("cid", p.clientId())
                    .addValue("dep", p.deploymentId())
                    .addValue("auth", p.authUrl())
                    .addValue("tok", p.tokenUrl())
                    .addValue("jwks", p.jwksUrl());
            jdbc.update(sql, params);
            return p;
        }
    }

    public void saveNonce(String nonce, String state, Instant expiresAt) {
        String sql = "INSERT INTO lti_nonces (nonce, state, expires_at) VALUES (:nonce, :state, :exp)";
        var params = new MapSqlParameterSource()
                .addValue("nonce", nonce)
                .addValue("state", state)
                .addValue("exp", java.sql.Timestamp.from(expiresAt));
        jdbc.update(sql, params);
    }

    /** Atomically validates and consumes a nonce to guarantee replay protection. */
    public boolean validateAndConsumeNonce(String nonce, String state) {
        String sql = """
                DELETE FROM lti_nonces
                WHERE nonce = :nonce AND state = :state AND expires_at > now()
                """;
        var params = new MapSqlParameterSource()
                .addValue("nonce", nonce)
                .addValue("state", state);
        int rows = jdbc.update(sql, params);
        return rows > 0;
    }

    public void mapCourse(long platformId, String contextId, long courseId) {
        String sql = """
                INSERT INTO lti_course_mappings (platform_id, lti_context_id, course_id)
                VALUES (:pid, :ctx, :cid)
                ON CONFLICT (platform_id, lti_context_id) DO UPDATE SET course_id = :cid
                """;
        var params = new MapSqlParameterSource()
                .addValue("pid", platformId)
                .addValue("ctx", contextId)
                .addValue("cid", courseId);
        jdbc.update(sql, params);
    }

    public Optional<Long> findMappedCourse(long platformId, String contextId) {
        String sql = "SELECT course_id FROM lti_course_mappings WHERE platform_id = :pid AND lti_context_id = :ctx";
        var params = new MapSqlParameterSource().addValue("pid", platformId).addValue("ctx", contextId);
        List<Long> list = jdbc.query(sql, params, (rs, rowNum) -> rs.getLong("course_id"));
        return list.stream().findFirst();
    }

    public void mapResourceLink(long platformId, String resourceLinkId, long assignmentId, String lineitemUrl) {
        String sql = """
                INSERT INTO lti_resource_links (platform_id, resource_link_id, assignment_id, lineitem_url)
                VALUES (:pid, :rl, :aid, :li)
                ON CONFLICT (platform_id, resource_link_id) DO UPDATE SET assignment_id = :aid, lineitem_url = :li
                """;
        var params = new MapSqlParameterSource()
                .addValue("pid", platformId)
                .addValue("rl", resourceLinkId)
                .addValue("aid", assignmentId)
                .addValue("li", lineitemUrl);
        jdbc.update(sql, params);
    }

    public Optional<Long> findMappedAssignment(long platformId, String resourceLinkId) {
        String sql = "SELECT assignment_id FROM lti_resource_links WHERE platform_id = :pid AND resource_link_id = :rl";
        var params = new MapSqlParameterSource().addValue("pid", platformId).addValue("rl", resourceLinkId);
        List<Long> list = jdbc.query(sql, params, (rs, rowNum) -> rs.getLong("assignment_id"));
        return list.stream().findFirst();
    }

    public Optional<String> findLineitemUrl(long platformId, long assignmentId) {
        String sql = "SELECT lineitem_url FROM lti_resource_links WHERE platform_id = :pid AND assignment_id = :aid AND lineitem_url IS NOT NULL";
        var params = new MapSqlParameterSource().addValue("pid", platformId).addValue("aid", assignmentId);
        List<String> list = jdbc.query(sql, params, (rs, rowNum) -> rs.getString("lineitem_url"));
        return list.stream().findFirst();
    }

    private LtiPlatform mapPlatform(ResultSet rs, int rowNum) throws SQLException {
        return new LtiPlatform(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("issuer"),
                rs.getString("client_id"),
                rs.getString("deployment_id"),
                rs.getString("auth_url"),
                rs.getString("token_url"),
                rs.getString("jwks_url"),
                rs.getTimestamp("created_at").toInstant()
        );
    }
}
