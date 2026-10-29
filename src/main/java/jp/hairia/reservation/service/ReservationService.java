package jp.hairia.reservation.service;

import jp.hairia.common.config.CurrentUser;
import jp.hairia.common.exception.ApiException;
import jp.hairia.customer.service.ProfileService;
import jp.hairia.system.service.AuditService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static jp.hairia.common.validation.Body.id;
import static jp.hairia.common.validation.Body.str;

@Service
public class ReservationService {
    private final JdbcTemplate jdbc;
    private final ProfileService profile;
    private final AuditService audit;

    public ReservationService(JdbcTemplate jdbc, ProfileService profile, AuditService audit) {
        this.jdbc = jdbc;
        this.profile = profile;
        this.audit = audit;
    }

    public List<Map<String, Object>> list(CurrentUser u) {
        return jdbc.queryForList("""
                select reservation_id as id, customer_id as c, hairdresser_id as s,
                       to_char(reserved_at, 'YYYY/MM/DD HH24:MI') as d,
                       case reservation_status when 'CANCELLED' then 'キャンセル' else '予約確定' end as st
                from reservations where %s = ? order by reserved_at""".formatted(u.isHairdresser() ? "hairdresser_id" : "customer_id"),
                u.profileId());
    }

    @Transactional
    public void create(CurrentUser u, Map<String, Object> b) {
        if (u.isHairdresser()) {
            throw ApiException.forbidden();
        }
        long hid = id(b, "stylistId");
        profile.requireLink(hid, u.profileId());
        int n = jdbc.update("""
                insert into reservations(customer_id, hairdresser_id, salon_id, reserved_at, reservation_status, menu_description)
                select ?, h.hairdresser_id, h.salon_id, ?::timestamp, 'CONFIRMED', '次回施術'
                from hairdressers h where h.hairdresser_id = ? and h.salon_id is not null""",
                u.profileId(), str(b, "slot"), hid);
        if (n == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "予約できる店舗が設定されていません");
        }
        audit.log(u.userId(), "予約");
    }

    @Transactional
    public void cancel(CurrentUser u, long id) {
        int n = jdbc.update("update reservations set reservation_status = 'CANCELLED' where reservation_id = ? and "
                + (u.isHairdresser() ? "hairdresser_id" : "customer_id") + " = ?", id, u.profileId());
        if (n == 0) {
            throw ApiException.forbidden();
        }
        audit.log(u.userId(), "予約キャンセル");
    }
}
