package jp.hairia.roadmap.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jp.hairia.common.config.CurrentUser;
import jp.hairia.common.exception.ApiException;
import jp.hairia.customer.service.ProfileService;
import jp.hairia.system.service.AuditService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RoadmapService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper om;
    private final ProfileService profile;
    private final AuditService audit;

    public RoadmapService(JdbcTemplate jdbc, ObjectMapper om, ProfileService profile, AuditService audit) {
        this.jdbc = jdbc;
        this.om = om;
        this.profile = profile;
        this.audit = audit;
    }

    /** 下書きのロードマップを作成する（AI分析から呼ばれる）。steps は w=時期 t=内容 e=期待 n=注意。 */
    @Transactional
    public void createDraft(long customerId, long hairdresserId, long counselingId, String goal, List<Map<String, String>> steps) {
        Long rid = jdbc.queryForObject("""
                insert into treatment_roadmaps(customer_id, counseling_id, goal_description, roadmap_status, created_by)
                values(?, ?, ?, 'DRAFT', ?) returning roadmap_id""", Long.class, customerId, counselingId, goal, hairdresserId);
        int n = 1;
        for (Map<String, String> s : steps) {
            jdbc.update("insert into roadmap_details(roadmap_id, step_number, planned_treatment, description, step_status) values(?, ?, ?, ?, 'PLANNED')",
                    rid, n++, s.get("t"), json(Map.of("w", s.get("w"), "e", s.get("e"), "n", s.get("n"))));
        }
    }

    /** お客様ごとの最新ロードマップ。お客様本人には共有済みのみ返す。 */
    public Map<String, Object> list(CurrentUser u) {
        var rows = u.isHairdresser()
                ? jdbc.queryForList("select distinct on (customer_id) roadmap_id, customer_id, roadmap_status from treatment_roadmaps where created_by = ? order by customer_id, roadmap_id desc", u.profileId())
                : jdbc.queryForList("select roadmap_id, customer_id, roadmap_status from treatment_roadmaps where customer_id = ? and roadmap_status = 'SHARED' order by roadmap_id desc limit 1", u.profileId());
        Map<String, Object> out = new LinkedHashMap<>();
        for (var r : rows) {
            List<Map<String, Object>> steps = new ArrayList<>();
            for (var d : jdbc.queryForList("select planned_treatment, description from roadmap_details where roadmap_id = ? order by step_number", r.get("roadmap_id"))) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("t", d.get("planned_treatment"));
                try {
                    m.putAll(om.readValue((String) d.get("description"), new TypeReference<Map<String, Object>>() {
                    }));
                } catch (JsonProcessingException | RuntimeException e) {
                    // 説明が壊れていても内容だけは表示する
                }
                steps.add(m);
            }
            out.put(String.valueOf(r.get("customer_id")), Map.of("steps", steps, "shared", "SHARED".equals(r.get("roadmap_status"))));
        }
        return out;
    }

    @Transactional
    public void share(CurrentUser u, long customerId) {
        if (!u.isHairdresser()) {
            throw ApiException.forbidden();
        }
        profile.requireLink(u.profileId(), customerId);
        profile.requireConsent(customerId);
        int n = jdbc.update("""
                update treatment_roadmaps set roadmap_status = 'SHARED'
                where roadmap_id = (select max(roadmap_id) from treatment_roadmaps where customer_id = ? and created_by = ?)""",
                customerId, u.profileId());
        if (n == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "共有するロードマップがありません");
        }
        audit.log(u.userId(), "ロードマップを共有");
    }

    private String json(Object o) {
        try {
            return om.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
