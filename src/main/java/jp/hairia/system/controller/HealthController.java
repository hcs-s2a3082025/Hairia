package jp.hairia.system.controller;

import jp.hairia.system.dto.HealthResponse;
import jp.hairia.system.service.HealthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class HealthController {
    private final HealthService healthService;

    public HealthController(HealthService healthService) {
        this.healthService = healthService;
    }

    @GetMapping
    public HealthResponse getHealth() {
        return healthService.getHealth();
    }
}
