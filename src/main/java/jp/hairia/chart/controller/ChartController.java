package jp.hairia.chart.controller;

import jp.hairia.chart.service.ChartService;
import jp.hairia.common.config.CurrentUser;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/charts")
public class ChartController {
    private final ChartService service;

    public ChartController(ChartService service) {
        this.service = service;
    }

    @GetMapping
    public List<Map<String, Object>> list(@RequestAttribute("me") CurrentUser me) {
        return service.list(me);
    }

    @PostMapping
    public Map<String, Object> create(@RequestAttribute("me") CurrentUser me, @RequestBody Map<String, Object> b) {
        return service.create(me, b);
    }

    @PutMapping("/{id}")
    public void update(@RequestAttribute("me") CurrentUser me, @PathVariable long id, @RequestBody Map<String, Object> b) {
        service.update(me, id, b);
    }

    @PostMapping("/{id}/advance")
    public void advance(@RequestAttribute("me") CurrentUser me, @PathVariable long id) {
        service.advance(me, id);
    }

    @PostMapping("/{id}/share")
    public void share(@RequestAttribute("me") CurrentUser me, @PathVariable long id) {
        service.share(me, id);
    }
}
