package jp.hairia.chart.service;

import jp.hairia.common.config.CurrentUser;
import jp.hairia.common.exception.ApiException;
import jp.hairia.customer.service.ProfileService;
import jp.hairia.system.service.AuditService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static jp.hairia.common.validation.Body.id;
import static jp.hairia.common.validation.Body.str;

/** カルテ。状態: 下書き→施術予定→施術実施中→確定→共有済み（画面の st = 0〜4）。 */
@Service
public class ChartService {
    private static final String[] STATUS = {"DRAFT", "PLANNED", "IN_PROGRESS", "CONFIRMED", "SHARED"};
    private static final String[] LABEL = {"下書き", "施術予定", "施術実施中", "確定", "共有済み"};
    private static final String SELECT = """
            select c.chart_id as id, c.customer_id as c, c.hairdresser_id as s,
                   to_char(t.treatment_date, 'YYYY/MM/DD') as d, c.chart_title as menu,
                   coalesce(t.treatment_content, '') as plan, coalesce(t.actual_result, '') as act,
                   coalesce(t.product_note, '') as dr,
                   case c.chart_status when 'DRAFT' then 0 when 'PLANNED' then 1 when 'IN_PROGRESS' then 2 when 'CONFIRMED' then 3 else 4 end as st,
                   %s as memo
            from charts c join treatment_histories t on t.chart_id = c.chart_id """;
    private final JdbcTemplate jdbc;
    private final ProfileService profile;
    private final AuditService audit;

    public ChartService(JdbcTemplate jdbc, ProfileService profile, AuditService audit) {
        this.jdbc = jdbc;
        this.profile = profile;
        this.audit = audit;
    }

    /** お客様には共有済みのカルテのみ返し、美容師メモは返さない。 */
    public List<Map<String, Object>> list(CurrentUser u) {
        return u.isHairdresser()
                ? jdbc.queryForList(SELECT.formatted("coalesce(c.internal_memo, '')") + " where c.hairdresser_id = ? order by c.chart_id", u.profileId())
                : jdbc.queryForList(SELECT.formatted("''::text") + " where c.customer_id = ? and c.chart_status = 'SHARED' order by c.chart_id", u.profileId());
    }

    @Transactional
    public Map<String, Object> create(CurrentUser u, Map<String, Object> b) {
        if (!u.isHairdresser()) {
            throw ApiException.forbidden();
        }
        long cid = id(b, "customerId");
        profile.requireLink(u.profileId(), cid);
        String menu = str(b, "menu").isEmpty() ? "カラー" : str(b, "menu");
        Long chart = jdbc.queryForObject("insert into charts(customer_id, hairdresser_id, chart_title, chart_status) values(?, ?, ?, 'DRAFT') returning chart_id",
                Long.class, cid, u.profileId(), menu);
        jdbc.update("insert into treatment_histories(chart_id, treatment_type, treatment_date, treatment_content, created_by) values(?, ?, current_date, ?, ?)",
                chart, menu, str(b, "plan"), u.profileId());
        audit.log(u.userId(), "カルテ作成（下書き）");
        return Map.of("id", chart);
    }

    @Transactional
    public void update(CurrentUser u, long id, Map<String, Object> b) {
        Map<String, Object> c = own(id, u);
        int i = index(c);
        String plan = (String) c.get("plan"), act = (String) c.get("act"), dr = (String) c.get("dr");
        String np = b.containsKey("plan") ? str(b, "plan") : plan;
        String na = b.containsKey("act") ? str(b, "act") : act;
        String nd = b.containsKey("dr") ? str(b, "dr") : dr;
        if (i >= 3) {
            if (!np.equals(plan) || !na.equals(act) || !nd.equals(dr)) {
                throw new ApiException(HttpStatus.CONFLICT, "確定後の施術内容は修正できません");
            }
        } else if (i >= 2) {
            np = plan;          // 施術開始後は予定を変更できない
        } else {
            na = act;           // 施術前は実績を入力できない
            nd = dr;
        }
        jdbc.update("update treatment_histories set treatment_content = ?, actual_result = ?, product_note = ? where chart_id = ?", np, na, nd, id);
        if (b.containsKey("memo")) {
            jdbc.update("update charts set internal_memo = ? where chart_id = ?", str(b, "memo"), id);
        }
    }

    @Transactional
    public void advance(CurrentUser u, long id) {
        Map<String, Object> c = own(id, u);
        int i = index(c);
        if (i >= 3) {
            throw new ApiException(HttpStatus.CONFLICT, "共有は共有確認から行ってください");
        }
        if (i == 2 && ((String) c.get("act")).isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "施術実績を入力してください");
        }
        jdbc.update("update charts set chart_status = ? where chart_id = ?", STATUS[i + 1], id);
        audit.log(u.userId(), "カルテ→" + LABEL[i + 1]);
    }

    @Transactional
    public void share(CurrentUser u, long id) {
        Map<String, Object> c = own(id, u);
        if (index(c) != 3) {
            throw new ApiException(HttpStatus.CONFLICT, "確定後に共有できます");
        }
        long cid = ((Number) c.get("customer_id")).longValue();
        profile.requireConsent(cid);
        jdbc.update("update charts set chart_status = 'SHARED' where chart_id = ?", id);
        jdbc.update("insert into chart_shares(chart_id, shared_by, shared_with, consent_status, shared_at) select ?, ?, c.user_id, 'GRANTED', now() from customers c where c.customer_id = ?",
                id, u.userId(), cid);
        audit.log(u.userId(), "カルテを共有");
    }

    private Map<String, Object> own(long id, CurrentUser u) {
        if (!u.isHairdresser()) {
            throw ApiException.forbidden();
        }
        var l = jdbc.queryForList("""
                select c.chart_id, c.customer_id, c.chart_status, coalesce(t.treatment_content, '') as plan,
                       coalesce(t.actual_result, '') as act, coalesce(t.product_note, '') as dr
                from charts c join treatment_histories t on t.chart_id = c.chart_id
                where c.chart_id = ? and c.hairdresser_id = ?""", id, u.profileId());
        if (l.isEmpty()) {
            throw ApiException.forbidden();
        }
        return l.get(0);
    }

    private int index(Map<String, Object> c) {
        return Arrays.asList(STATUS).indexOf(c.get("chart_status"));
    }
}
