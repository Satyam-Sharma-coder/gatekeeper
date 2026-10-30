package in.org.gatekeeper.analytics_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@AllArgsConstructor
@Getter
@Setter
public class HealthResponse {

    String appName;
    String appDescription;
    String appHealth;


}
