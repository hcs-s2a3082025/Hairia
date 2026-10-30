package jp.hairia.notification.controller;

import jp.hairia.common.config.CurrentUser;
import jp.hairia.notification.service.NotificationService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    public List<Map<String, Object>> list(@RequestAttribute("me") CurrentUser me) {
        return service.list(me.userId());
    }

    @PostMapping("/read")
    public void readAll(@RequestAttribute("me") CurrentUser me) {
        service.readAll(me.userId());
    }
}
