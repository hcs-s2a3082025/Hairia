package jp.hairia.questionnaire.service;

import jp.hairia.common.config.CurrentUser;
import jp.hairia.common.exception.ApiException;
import jp.hairia.system.service.AuditService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

import static jp.hairia.common.validation.Body.str;

@Service
public class QuestionnaireService {
    // 画面のキー -> questionnaires.question_type
    private static final Map<String, String> TYPES = Map.of(
            "fade", "FADE", "vol", "VOLUME", "tex", "TEXTURE", "dmg", "DAMAGE", "home", "HOMECARE", "want", "WISH");
    private final JdbcTemplate jdbc;
    private final AuditService audit;

    public QuestionnaireService(JdbcTemplate jdbc, AuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Transactional
    public void answer(CurrentUser u, Map<String, Object> b) {
        if (u.isHairdresser()) {
            throw ApiException.forbidden();
        }
        var res = jdbc.queryForList("""
                select reservation_id from reservations
                where customer_id = ? and reservation_status <> 'CANCELLED'
                order by reserved_at desc limit 1""", u.profileId());
        if (res.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "予約後にアンケートへ回答できます");
        }
        Object rid = res.get(0).get("reservation_id");
        jdbc.update("delete from questionnaire_answers where reservation_id = ? and customer_id = ?", rid, u.profileId());
        TYPES.forEach((key, type) -> jdbc.update("""
                insert into questionnaire_answers(questionnaire_id, reservation_id, customer_id, answer_text)
                select questionnaire_id, ?, ?, ? from questionnaires where question_type = ? and salon_id is null""",
                rid, u.profileId(), str(b, key), type));
        audit.log(u.userId(), "事前アンケート回答");
    }

    /** 直近のアンケート回答。未回答なら null。 */
    public Map<String, Object> latest(long customerId) {
        var rows = jdbc.queryForList("""
                select q.question_type as t, a.answer_text as v
                from questionnaire_answers a join questionnaires q on q.questionnaire_id = a.questionnaire_id
                where a.customer_id = ?
                  and a.reservation_id = (select max(reservation_id) from questionnaire_answers where customer_id = ?)
                """, customerId, customerId);
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, String> byType = new LinkedHashMap<>();
        rows.forEach(r -> byType.put((String) r.get("t"), (String) r.get("v")));
        Map<String, Object> out = new LinkedHashMap<>();
        TYPES.forEach((key, type) -> out.put(key, byType.getOrDefault(type, "")));
        try {
            out.put("dmg", Integer.parseInt(String.valueOf(out.get("dmg"))));
        } catch (NumberFormatException e) {
            out.put("dmg", 0);
        }
        return out;
    }
}
