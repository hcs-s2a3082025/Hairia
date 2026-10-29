package jp.hairia.counseling.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jp.hairia.common.config.CurrentUser;
import jp.hairia.common.exception.ApiException;
import jp.hairia.customer.service.ProfileService;
import jp.hairia.roadmap.service.RoadmapService;
import jp.hairia.system.service.AuditService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static jp.hairia.common.validation.Body.id;
import static jp.hairia.common.validation.Body.num;
import static jp.hairia.common.validation.Body.str;

/**
 * カウンセリングの保存と分析。現在はルールベース（model_version = rule-v1）で、
 * 将来AIモデルに差し替えられるよう分析ロジックをこのクラスに閉じています。
 * 分析結果は参考情報であり、最終判断は美容師が行います。
 */
@Service
public class CounselingService {
    private static final Map<String, Double> COLOR_LEVEL = Map.of(
            "ナチュラルブラウン", 0.0, "アッシュブラウン", 1.0, "ミルクティーベージュ", 2.5, "ピンクベージュ", 2.0, "アッシュグレー", 3.5);
    private final JdbcTemplate jdbc;
    private final ObjectMapper om;
    private final ProfileService profile;
    private final RoadmapService roadmaps;
    private final AuditService audit;

    public CounselingService(JdbcTemplate jdbc, ObjectMapper om, ProfileService profile, RoadmapService roadmaps, AuditService audit) {
        this.jdbc = jdbc;
        this.om = om;
        this.profile = profile;
        this.roadmaps = roadmaps;
        this.audit = audit;
    }

    @Transactional
    public Map<String, Object> analyze(CurrentUser u, Map<String, Object> b) {
        if (!u.isHairdresser()) {
            throw ApiException.forbidden();
        }
        long cid = id(b, "customerId");
        profile.requireLink(u.profileId(), cid);
        boolean color = "color".equals(str(b, "type"));
        String col = str(b, "col").isEmpty() ? "アッシュブラウン" : str(b, "col");
        int dmg = (int) Math.max(1, Math.min(5, num(b, "dm", 3)));
        boolean bleach = num(b, "bl", 0) > 0, dyed = num(b, "co", 0) > 0, perm = num(b, "pm", 0) > 0, straight = num(b, "sc", 0) > 0;
        double score = COLOR_LEVEL.getOrDefault(col, 1.0) + (dmg - 1) * 0.6 + (bleach ? 0.8 : 0) + (perm ? 0.4 : 0);
        String level = score < 2.2 ? "A" : score < 3.6 ? "B" : "C";

        long counseling = jdbc.queryForObject("""
                insert into counselings(customer_id, hairdresser_id, counseling_type, counseling_status, completed_at)
                values(?, ?, ?, 'COMPLETED', now()) returning counseling_id""", Long.class, cid, u.profileId(), color ? "COLOR" : "HAIR");
        List<String> history = new ArrayList<>();
        if (bleach) history.add("ブリーチ");
        if (dyed) history.add("カラー");
        if (perm) history.add("パーマ");
        if (straight) history.add("縮毛矯正");
        long condition = jdbc.queryForObject("""
                insert into hair_conditions(counseling_id, hair_thickness, curl_type, damage_level, treatment_history, notes)
                values(?, ?, ?, ?, ?, ?) returning hair_condition_id""", Long.class,
                counseling, str(b, "th"), str(b, "cu"), dmg, String.join(",", history), str(b, "note"));
        jdbc.update("""
                insert into hair_analysis_results(hair_condition_id, estimated_damage_level, estimated_curl_type, analysis_result, model_version)
                values(?, ?, ?, ?::jsonb, 'rule-v1')""", condition, dmg, str(b, "cu"), json(Map.of("score", score)));
        if (color) {
            long desired = jdbc.queryForObject("""
                    insert into desired_colors(counseling_id, color_name, bleaching_preference, notes)
                    values(?, ?, ?, ?) returning desired_color_id""", Long.class, counseling, col, "C".equals(level), str(b, "st"));
            jdbc.update("""
                    insert into color_analysis_results(desired_color_id, feasibility, estimated_sessions, analysis_result, cautions, model_version)
                    values(?, ?, ?, ?::jsonb, ?, 'rule-v1')""", desired, level, "C".equals(level) ? 2 : 1, json(Map.of("score", score)),
                    (bleach || dmg >= 4) ? "ブリーチ履歴またはダメージが強いため、髪への負担に注意" : null);
        }
        roadmaps.createDraft(cid, u.profileId(), counseling, col, steps(col, level));
        audit.log(u.userId(), "AI分析を実行");
        return Map.of("lv", level, "r", score);
    }

    private List<Map<String, String>> steps(String col, String level) {
        boolean c = "C".equals(level);
        List<Map<String, String>> l = new ArrayList<>();
        l.add(step("今回", c ? "1回目：明度アップ（ブリーチ）" : "カラー（" + col + "）", c ? "下地を整える" : "希望の色味に近づく", "髪の状態を見て当日調整"));
        if (c) {
            l.add(step("約4週間後", "2回目：" + col + " へ色味調整", "希望カラーに到達", "間にトリートメントを推奨"));
        }
        l.add(step("約6週間後（12月上旬）", "トリートメント＋根元リタッチ", "手触り・ツヤの維持", "ホームケアを併用"));
        l.add(step("約3か月後", "カット＋カラー", "形と色の再調整", "状態により内容は変更可"));
        return l;
    }

    private Map<String, String> step(String w, String t, String e, String n) {
        return Map.of("w", w, "t", t, "e", e, "n", n);
    }

    private String json(Object o) {
        try {
            return om.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
