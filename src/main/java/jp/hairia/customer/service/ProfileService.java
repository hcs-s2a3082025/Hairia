package jp.hairia.customer.service;

import jp.hairia.notification.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import jp.hairia.common.config.CurrentUser;
import jp.hairia.common.config.Tokens;
import jp.hairia.common.exception.ApiException;
import jp.hairia.questionnaire.service.QuestionnaireService;
import jp.hairia.system.service.AuditService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static jp.hairia.common.validation.Body.bool;
import static jp.hairia.common.validation.Body.imageUri;
import static jp.hairia.common.validation.Body.opt;

@Service
public class ProfileService {
    @Autowired
    private NotificationService notify;

    private final JdbcTemplate jdbc;
    private final QuestionnaireService questionnaires;
    private final AuditService audit;

    public ProfileService(JdbcTemplate jdbc, QuestionnaireService questionnaires, AuditService audit) {
        this.jdbc = jdbc;
        this.questionnaires = questionnaires;
        this.audit = audit;
    }

    public Map<String, Object> me(CurrentUser u) {
        if (u.isHairdresser()) {
            return jdbc.queryForMap("""
                    select h.hairdresser_id as id, 'STYLIST'::text as role, h.display_name as n, coalesce(h.gender, '') as g,
                           coalesce(s.salon_name, '') as shop, coalesce(h.avatar_uri, '') as av
                    from hairdressers h left join salons s on s.salon_id = h.salon_id where h.user_id = ?""", u.userId());
        }
        return jdbc.queryForMap("""
                select customer_id as id, 'CUSTOMER'::text as role, display_name as n, coalesce(gender, '') as g,
                       coalesce(avatar_uri, '') as av, consent_share as cons, consent_ai as "aiOk", consent_external as ext
                from customers where user_id = ?""", u.userId());
    }

    @Transactional
    public void updateMe(CurrentUser u, Map<String, Object> b) {
        String av = opt(b, "av");
        if (av != null && !av.isEmpty()) {
            imageUri(av, 700_000);
        }
        if (u.isHairdresser()) {
            jdbc.update("""
                    update hairdressers set display_name = coalesce(nullif(?::text, ''), display_name),
                           gender = coalesce(?::text, gender), avatar_uri = coalesce(?::text, avatar_uri)
                    where hairdresser_id = ?""", opt(b, "name"), opt(b, "gender"), av, u.profileId());
            String shop = opt(b, "shop");
            if (shop != null && !shop.isBlank()) {
                jdbc.update("update salons set salon_name = ? where salon_id = (select salon_id from hairdressers where hairdresser_id = ?)",
                        shop.trim(), u.profileId());
            }
        } else {
            jdbc.update("""
                    update customers set display_name = coalesce(nullif(?::text, ''), display_name),
                           gender = coalesce(?::text, gender), avatar_uri = coalesce(?::text, avatar_uri),
                           consent_share = coalesce(?::boolean, consent_share), consent_ai = coalesce(?::boolean, consent_ai),
                           consent_external = coalesce(?::boolean, consent_external)
                    where customer_id = ?""", opt(b, "name"), opt(b, "gender"), av,
                    bool(b, "cons"), bool(b, "aiOk"), bool(b, "ext"), u.profileId());
        }
    }

    /** 美容師: 連携中のお客様 / お客様: 自分自身。 */
    public List<Map<String, Object>> customers(CurrentUser u) {
        String sql = """
                select c.customer_id as id, c.display_name as n, coalesce(c.hair_note, '') as h, c.consent_share as cons,
                       coalesce(c.gender, '') as g, coalesce(c.avatar_uri, '') as av from customers c """;
        List<Map<String, Object>> rows = u.isHairdresser()
                ? jdbc.queryForList(sql + " where c.customer_id in (select customer_id from customer_links where hairdresser_id = ? and link_status = 'ACTIVE') order by c.customer_id", u.profileId())
                : jdbc.queryForList(sql + " where c.customer_id = ?", u.profileId());
        rows.forEach(m -> m.put("sv", questionnaires.latest(((Number) m.get("id")).longValue())));
        return rows;
    }

    /** お客様: 連携中の美容師 / 美容師: 自分自身。 */
    public List<Map<String, Object>> hairdressers(CurrentUser u) {
        String sql = """
                select h.hairdresser_id as id, h.display_name as n, coalesce(s.salon_name, '') as shop,
                       coalesce(h.gender, '') as g, coalesce(h.avatar_uri, '') as av
                from hairdressers h left join salons s on s.salon_id = h.salon_id """;
        return u.isHairdresser()
                ? jdbc.queryForList(sql + " where h.hairdresser_id = ?", u.profileId())
                : jdbc.queryForList(sql + " where h.hairdresser_id in (select hairdresser_id from customer_links where customer_id = ? and link_status = 'ACTIVE') order by h.hairdresser_id", u.profileId());
    }

    public void requireLink(long hairdresserId, long customerId) {
        Integer n = jdbc.queryForObject("select count(*) from customer_links where hairdresser_id = ? and customer_id = ? and link_status = 'ACTIVE'",
                Integer.class, hairdresserId, customerId);
        if (n == null || n == 0) {
            throw new ApiException(HttpStatus.FORBIDDEN, "連携されていないお客様です");
        }
    }

    public void requireConsent(long customerId) {
        Boolean ok = jdbc.queryForObject("select consent_share from customers where customer_id = ?", Boolean.class, customerId);
        if (!Boolean.TRUE.equals(ok)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "お客様の同意がありません");
        }
    }

    // ---- QRコード連携（トークンはハッシュで保存・10分・1回限り）
    @Transactional
    public String createQr(CurrentUser u) {
        if (!u.isHairdresser()) {
            throw ApiException.forbidden();
        }
        String code = Tokens.code(8);
        jdbc.update("insert into qr_codes(hairdresser_id, token_hash, expires_at) values(?, ?, now() + interval '10 minutes')",
                u.profileId(), Tokens.sha256(code));
        return code;
    }

    @Transactional
    public void redeemQr(CurrentUser u, String token) {
        if (u.isHairdresser()) {
            throw ApiException.forbidden();
        }
        var q = jdbc.queryForList("select qr_code_id, hairdresser_id from qr_codes where token_hash = ? and used_at is null and expires_at > now() for update",
                Tokens.sha256(token.trim().toUpperCase()));
        if (q.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "QRコードが無効か期限切れです");
        }
        long hid = ((Number) q.get(0).get("hairdresser_id")).longValue();
        jdbc.update("update qr_codes set used_at = now() where qr_code_id = ?", q.get(0).get("qr_code_id"));
        var ex = jdbc.queryForList("select link_id from customer_links where hairdresser_id = ? and customer_id = ? and link_status = 'ACTIVE'", hid, u.profileId());
        long link = ex.isEmpty()
                ? jdbc.queryForObject("insert into customer_links(hairdresser_id, customer_id, link_status, consented_at) values(?, ?, 'ACTIVE', now()) returning link_id", Long.class, hid, u.profileId())
                : ((Number) ex.get(0).get("link_id")).longValue();
        jdbc.update("insert into chat_rooms(link_id, room_status) select ?, 'ACTIVE' where not exists (select 1 from chat_rooms where link_id = ?)", link, link);
        audit.log(u.userId(), "美容師と連携");
        notify.toPartner(u, hid, "LINK", "新しいお客様と連携しました", "");
    }

    /** お客様が美容師との連携を解除する（履歴は残る）。 */
    @Transactional
    public void endLink(CurrentUser u, long hairdresserId) {
        if (u.isHairdresser()) {
            throw ApiException.forbidden();
        }
        int n = jdbc.update("update customer_links set link_status = 'ENDED' where hairdresser_id = ? and customer_id = ? and link_status = 'ACTIVE'",
                hairdresserId, u.profileId());
        if (n == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "連携が見つかりません");
        }
        audit.log(u.userId(), "美容師との連携を解除");
        notify.toPartner(u, hairdresserId, "LINK", "お客様が連携を解除しました", "");
    }

    /** 退会。ログインを停止し、連携を終了する。 */
    @Transactional
    public void withdraw(CurrentUser u) {
        jdbc.update("update users set account_status = 'WITHDRAWN' where user_id = ?", u.userId());
        jdbc.update("delete from auth_sessions where user_id = ?", u.userId());
        jdbc.update("update customer_links set link_status = 'ENDED' where " + (u.isHairdresser() ? "hairdresser_id" : "customer_id") + " = ?", u.profileId());
        audit.log(u.userId(), "退会");
    }
}
