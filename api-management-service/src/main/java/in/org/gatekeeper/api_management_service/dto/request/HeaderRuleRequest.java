package in.org.gatekeeper.api_management_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class HeaderRuleRequest {

    @NotBlank
    @Size(max = 20)
    private String direction;

    @NotBlank
    @Size(max = 20)
    private String action;

    @NotBlank
    @Size(max = 200)
    private String headerName;

    @Size(max = 2000)
    private String headerValue;
}
