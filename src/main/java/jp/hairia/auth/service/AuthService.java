package jp.hairia.auth.service;

import jp.hairia.common.config.Tokens;
import jp.hairia.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static jp.hairia.common.validation.Body.str;

@Service
public class AuthService {
    private final JdbcTemplate jdbc;
    private final BCryptPasswordEncoder enc;

    public AuthService(JdbcTemplate jdbc, BCryptPasswordEncoder enc) {
        this.jdbc = jdbc;
        this.enc = enc;
    }

    /** お客様のみ新規登録できます（美容師アカウントは店舗側で発行）。 */
    @Transactional
    public Map<String, Object> signup(Map<String, Object> b) {
        String email = str(b, "email").toLowerCase();
        String pw = str(b, "password");
        String name = str(b, "name");
        if (!email.contains("@") || pw.length() < 8) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "メールアドレスと8文字以上のパスワードを入力してください");
        }
        Integer n = jdbc.queryForObject("select count(*) from users where email = ?", Integer.class, email);
        if (n != null && n > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "このメールアドレスは既に登録されています");
        }
        Long uid = jdbc.queryForObject(
                "insert into users(email, password_hash, user_type, account_status) values(?, ?, 'CUSTOMER', 'ACTIVE') returning user_id",
                Long.class, email, enc.encode(pw));
        jdbc.update("insert into customers(user_id, display_name) values(?, ?)", uid, name.isEmpty() ? email : name);
        return Map.of("token", newSession(uid));
    }

    public Map<String, Object> login(Map<String, Object> b) {
        var rows = jdbc.queryForList("select user_id, password_hash, account_status from users where email = ?",
                str(b, "email").toLowerCase());
        if (rows.isEmpty() || !enc.matches(str(b, "password"), (String) rows.get(0).get("password_hash"))
                || !"ACTIVE".equals(rows.get(0).get("account_status"))) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "メールアドレスまたはパスワードが違います");
        }
        return Map.of("token", newSession(((Number) rows.get(0).get("user_id")).longValue()));
    }

    public void logout(String header) {
        if (header != null && header.startsWith("Bearer ")) {
            jdbc.update("delete from auth_sessions where token_hash = ?", Tokens.sha256(header.substring(7)));
        }
    }

    private String newSession(long userId) {
        String token = Tokens.random(32);
        jdbc.update("insert into auth_sessions(token_hash, user_id, expires_at) values(?, ?, now() + interval '7 days')",
                Tokens.sha256(token), userId);
        return token;
    }
}
