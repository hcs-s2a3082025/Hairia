package jp.hairia.chat.service;

import jp.hairia.notification.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import jp.hairia.common.config.CurrentUser;
import jp.hairia.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static jp.hairia.common.validation.Body.id;
import static jp.hairia.common.validation.Body.imageUri;
import static jp.hairia.common.validation.Body.str;

@Service
public class ChatService {
    @Autowired
    private NotificationService notify;

    private final JdbcTemplate jdbc;

    public ChatService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** r = 送信者以外が既読にしたか。 */
    public List<Map<String, Object>> messages(CurrentUser u) {
        return jdbc.queryForList("""
                select m.message_id as id, l.customer_id as c, l.hairdresser_id as s,
                       case when su.user_type = 'HAIRDRESSER' then 's' else 'c' end as f,
                       coalesce(m.message_text, '') as t, m.attachment_uri as img,
                       exists (select 1 from message_reads r where r.message_id = m.message_id and r.user_id <> m.sender_user_id) as r
                from chat_messages m
                join chat_rooms cr on cr.chat_room_id = m.chat_room_id
                join customer_links l on l.link_id = cr.link_id
                join users su on su.user_id = m.sender_user_id
                where l.%s = ? and l.link_status = 'ACTIVE' order by m.message_id""".formatted(u.isHairdresser() ? "hairdresser_id" : "customer_id"),
                u.profileId());
    }

    @Transactional
    public void send(CurrentUser u, Map<String, Object> b) {
        long room = room(u, id(b, "withId"));
        String text = str(b, "body");
        String image = imageUri(b.get("image") instanceof String s ? s : null, 1_500_000);
        if (image == null && text.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "メッセージを入力してください");
        }
        jdbc.update("insert into chat_messages(chat_room_id, sender_user_id, message_type, message_text, attachment_uri) values(?, ?, ?, ?, ?)",
                room, u.userId(), image != null ? "IMAGE" : "TEXT", image != null ? null : text, image);
        notify.toPartner(u, id(b, "withId"), "CHAT", "新着メッセージ",
                image != null ? "写真が届きました" : (text.length() > 30 ? text.substring(0, 30) + "…" : text));
    }

    @Transactional
    public void read(CurrentUser u, long withId) {
        long room = room(u, withId);
        jdbc.update("""
                insert into message_reads(message_id, user_id)
                select message_id, ? from chat_messages where chat_room_id = ? and sender_user_id <> ?
                on conflict (message_id, user_id) do nothing""", u.userId(), room, u.userId());
    }

    private long room(CurrentUser u, long other) {
        long hid = u.isHairdresser() ? u.profileId() : other;
        long cid = u.isHairdresser() ? other : u.profileId();
        var l = jdbc.queryForList("""
                select cr.chat_room_id from chat_rooms cr join customer_links k on k.link_id = cr.link_id
                where k.hairdresser_id = ? and k.customer_id = ? and k.link_status = 'ACTIVE'""", hid, cid);
        if (l.isEmpty()) {
            throw ApiException.forbidden();
        }
        return ((Number) l.get(0).get("chat_room_id")).longValue();
    }
}
