package jp.hairia.system.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuditService {
    private final JdbcTemplate jdbc;

    public AuditService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void log(long userId, String text) {
        jdbc.update("insert into audit_logs(user_id, action_text) values(?, ?)", userId, text);
    }

    public List<String> recent(long userId) {
        return jdbc.queryForList("""
                select to_char(created_at, 'HH24:MI') || '　' || action_text
                from audit_logs where user_id = ? order by audit_id desc limit 30
                """, String.class, userId);
    }
}
