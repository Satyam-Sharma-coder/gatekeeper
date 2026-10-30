package in.org.gatekeeper.api_management_service.controller;

import in.org.gatekeeper.api_management_service.dto.response.HealthResponse;
import in.org.gatekeeper.api_management_service.service.HealthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequiredArgsConstructor
@RequestMapping("/health")
public class HealthController {

    public final HealthService healthService;

    @GetMapping
    public ResponseEntity<HealthResponse> health() {
        String dbStatus = healthService.getHealth();
        return ResponseEntity.ok(
                new HealthResponse("UP", dbStatus, LocalDateTime.now()));
    }
}
