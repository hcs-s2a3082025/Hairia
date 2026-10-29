package jp.hairia.common.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {
    private final JdbcTemplate jdbc;

    public AuthInterceptor(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) throws Exception {
        String h = req.getHeader("Authorization");
        if (h != null && h.startsWith("Bearer ")) {
            var rows = jdbc.query("""
                    select u.user_id, u.user_type, coalesce(hd.hairdresser_id, c.customer_id) as profile_id
                    from auth_sessions s
                    join users u on u.user_id = s.user_id
                    left join hairdressers hd on hd.user_id = u.user_id
                    left join customers c on c.user_id = u.user_id
                    where s.token_hash = ? and s.expires_at > now() and u.account_status = 'ACTIVE'
                    """, (rs, i) -> new CurrentUser(rs.getLong(1), rs.getString(2), rs.getLong(3)),
                    Tokens.sha256(h.substring(7)));
            if (!rows.isEmpty()) {
                req.setAttribute("me", rows.get(0));
                return true;
            }
        }
        res.setStatus(401);
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write("{\"message\":\"ログインが必要です\"}");
        return false;
    }
}
