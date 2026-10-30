package in.org.gatekeeper.api_management_service.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RouteStatusRequest {

    @NotNull
    private Boolean active;
}
