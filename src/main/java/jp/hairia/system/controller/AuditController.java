package jp.hairia.system.controller;

import jp.hairia.common.config.CurrentUser;
import jp.hairia.system.service.AuditService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/audit")
public class AuditController {
    private final AuditService audit;

    public AuditController(AuditService audit) {
        this.audit = audit;
    }

    @GetMapping
    public List<String> recent(@RequestAttribute("me") CurrentUser me) {
        return me.isHairdresser() ? audit.recent(me.userId()) : List.of();
    }
}
