package jp.hairia.notification.service;

import jp.hairia.common.config.CurrentUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class NotificationService {
    private final JdbcTemplate jdbc;

    public NotificationService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void toUser(long userId, String type, String title, String body) {
        jdbc.update("insert into notifications(user_id, notification_type, title, body) values(?, ?, ?, ?)", userId, type, title, body);
    }

    /** 操作した本人の「相手」（美容師→お客様 / お客様→美容師）へ通知する。partnerProfileId は相手の hairdresser_id / customer_id。 */
    public void toPartner(CurrentUser from, long partnerProfileId, String type, String title, String body) {
        List<Long> ids = from.isHairdresser()
                ? jdbc.queryForList("select user_id from customers where customer_id = ?", Long.class, partnerProfileId)
                : jdbc.queryForList("select user_id from hairdressers where hairdresser_id = ?", Long.class, partnerProfileId);
        ids.forEach(id -> toUser(id, type, title, body));
    }

    public List<Map<String, Object>> list(long userId) {
        return jdbc.queryForList("""
                select notification_id as id, notification_type as t, title, coalesce(body, '') as body,
                       is_read as "read", to_char(created_at, 'MM/DD HH24:MI') as tm
                from notifications where user_id = ? order by notification_id desc limit 30""", userId);
    }

    public void readAll(long userId) {
        jdbc.update("update notifications set is_read = true where user_id = ? and not is_read", userId);
    }
}
