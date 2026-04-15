package com.caseflow.ai.api;

import com.caseflow.ai.api.dto.HealthReadyResponse;
import com.caseflow.ai.api.dto.ModelStatusResponse;
import com.caseflow.ai.service.HealthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai/health")
@RequiredArgsConstructor
@Slf4j
public class HealthController {

    private final HealthService healthService;

    @GetMapping("/ready")
    public ResponseEntity<HealthReadyResponse> ready() {
        log.info("GET /api/ai/health/ready");
        return ResponseEntity.ok(healthService.getReadiness());
    }

    @GetMapping("/models")
    public ResponseEntity<ModelStatusResponse> models() {
        log.info("GET /api/ai/health/models");
        return ResponseEntity.ok(healthService.getModelStatus());
    }
}
