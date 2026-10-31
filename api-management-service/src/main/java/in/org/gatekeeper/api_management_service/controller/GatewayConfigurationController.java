package in.org.gatekeeper.api_management_service.controller;

import in.org.gatekeeper.api_management_service.dto.response.RouteResponse;
import in.org.gatekeeper.api_management_service.service.RouteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/gateway/routes")
public class GatewayConfigurationController {

    private final RouteService routeService;

    @GetMapping
    public ResponseEntity<List<RouteResponse>> getActiveRoutes() {
        return ResponseEntity.ok(routeService.findAllActiveForGateway());
    }
}