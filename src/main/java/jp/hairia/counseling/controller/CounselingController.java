package jp.hairia.counseling.controller;

import jp.hairia.common.config.CurrentUser;
import jp.hairia.counseling.service.CounselingService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/counselings")
public class CounselingController {
    private final CounselingService service;

    public CounselingController(CounselingService service) {
        this.service = service;
    }

    @PostMapping("/analysis")
    public Map<String, Object> analyze(@RequestAttribute("me") CurrentUser me, @RequestBody Map<String, Object> b) {
        return service.analyze(me, b);
    }
}
