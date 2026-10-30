package jp.hairia.auth.controller;

import jp.hairia.auth.service.AuthService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/signup")
    public Map<String, Object> signup(@RequestBody Map<String, Object> b) {
        return auth.signup(b);
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, Object> b) {
        return auth.login(b);
    }

    @PostMapping("/logout")
    public void logout(@RequestHeader(value = "Authorization", required = false) String h) {
        auth.logout(h);
    }

    @PostMapping("/password-reset/request")
    public Map<String, Object> requestReset(@RequestBody Map<String, Object> b) {
        return auth.requestReset(b);
    }

    @PostMapping("/password-reset/confirm")
    public void confirmReset(@RequestBody Map<String, Object> b) {
        auth.confirmReset(b);
    }
}
