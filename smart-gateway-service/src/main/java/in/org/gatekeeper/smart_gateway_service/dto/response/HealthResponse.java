package in.org.gatekeeper.smart_gateway_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@AllArgsConstructor
@Getter
@Setter
public class HealthResponse {
    String ServiceName;
    String ServiceDescription;
    String appStatus;
}
