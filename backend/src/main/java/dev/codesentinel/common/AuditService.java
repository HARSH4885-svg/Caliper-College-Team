package dev.codesentinel.common;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
    private final JdbcClient jdbc;

    public AuditService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void log(Long actorId, String action, String entityType, Long entityId, String detail) {
        jdbc.sql("INSERT INTO audit_logs(actor_id, action, entity_type, entity_id, detail) VALUES (?,?,?,?,?)")
                .params(actorId, action, entityType, entityId, detail == null ? null : (detail.length() > 500 ? detail.substring(0, 500) : detail)).update();
    }
}
