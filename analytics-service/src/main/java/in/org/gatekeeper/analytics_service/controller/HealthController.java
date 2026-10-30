package in.org.gatekeeper.analytics_service.controller;

import in.org.gatekeeper.analytics_service.dto.response.HealthResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/health")
public class HealthController {

    private String appName;
    private String appDescription;

    public HealthController(@Value("${info.app.name}") String appName,
                            @Value("${info.app.description}") String appDescription){
        this.appName=appName;
        this.appDescription=appDescription;
    }

    @GetMapping
    public ResponseEntity<HealthResponse> health(){
        return ResponseEntity.ok(new HealthResponse(appName,appDescription,"UP"));
    }
}

