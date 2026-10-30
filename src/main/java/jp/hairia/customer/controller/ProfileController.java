package jp.hairia.customer.controller;

import jp.hairia.common.config.CurrentUser;
import jp.hairia.customer.service.ProfileService;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static jp.hairia.common.validation.Body.str;

@RestController
@RequestMapping("/api")
public class ProfileController {
    private final ProfileService profile;

    public ProfileController(ProfileService profile) {
        this.profile = profile;
    }

    @GetMapping("/me")
    public Map<String, Object> me(@RequestAttribute("me") CurrentUser me) {
        return profile.me(me);
    }

    @PutMapping("/me")
    public void updateMe(@RequestAttribute("me") CurrentUser me, @RequestBody Map<String, Object> b) {
        profile.updateMe(me, b);
    }

    @GetMapping("/customers")
    public List<Map<String, Object>> customers(@RequestAttribute("me") CurrentUser me) {
        return profile.customers(me);
    }

    /** id をキーにしたオブジェクトで返す。 */
    @GetMapping("/hairdressers")
    public Map<String, Object> hairdressers(@RequestAttribute("me") CurrentUser me) {
        Map<String, Object> out = new LinkedHashMap<>();
        profile.hairdressers(me).forEach(h -> out.put(String.valueOf(h.get("id")), h));
        return out;
    }

    @PostMapping("/qr")
    public Map<String, Object> createQr(@RequestAttribute("me") CurrentUser me) {
        return Map.of("token", profile.createQr(me), "expiresIn", 600);
    }

    @PostMapping("/qr/redeem")
    public void redeem(@RequestAttribute("me") CurrentUser me, @RequestBody Map<String, Object> b) {
        profile.redeemQr(me, str(b, "token"));
    }

    @PostMapping("/links/{hairdresserId}/end")
    public void endLink(@RequestAttribute("me") CurrentUser me, @PathVariable long hairdresserId) {
        profile.endLink(me, hairdresserId);
    }

    @PostMapping("/me/withdraw")
    public void withdraw(@RequestAttribute("me") CurrentUser me) {
        profile.withdraw(me);
    }
}
