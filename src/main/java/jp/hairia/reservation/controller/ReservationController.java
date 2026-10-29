package jp.hairia.reservation.controller;

import jp.hairia.common.config.CurrentUser;
import jp.hairia.reservation.service.ReservationService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reservations")
public class ReservationController {
    private final ReservationService service;

    public ReservationController(ReservationService service) {
        this.service = service;
    }

    @GetMapping
    public List<Map<String, Object>> list(@RequestAttribute("me") CurrentUser me) {
        return service.list(me);
    }

    @PostMapping
    public void create(@RequestAttribute("me") CurrentUser me, @RequestBody Map<String, Object> b) {
        service.create(me, b);
    }

    @PostMapping("/{id}/cancel")
    public void cancel(@RequestAttribute("me") CurrentUser me, @PathVariable long id) {
        service.cancel(me, id);
    }
}
