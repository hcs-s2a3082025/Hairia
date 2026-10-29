package jp.hairia.roadmap.controller;

import jp.hairia.common.config.CurrentUser;
import jp.hairia.roadmap.service.RoadmapService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/roadmaps")
public class RoadmapController {
    private final RoadmapService service;

    public RoadmapController(RoadmapService service) {
        this.service = service;
    }

    @GetMapping
    public Map<String, Object> list(@RequestAttribute("me") CurrentUser me) {
        return service.list(me);
    }

    @PostMapping("/{customerId}/share")
    public void share(@RequestAttribute("me") CurrentUser me, @PathVariable long customerId) {
        service.share(me, customerId);
    }
}
