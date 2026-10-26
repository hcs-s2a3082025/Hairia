package jp.hairia.system.service;

import jp.hairia.system.dto.HealthResponse;
import org.springframework.stereotype.Service;

@Service
public class HealthService {
    public HealthResponse getHealth() {
        return new HealthResponse("UP");
    }
}
