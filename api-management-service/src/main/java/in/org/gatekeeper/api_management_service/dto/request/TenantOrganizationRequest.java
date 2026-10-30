package in.org.gatekeeper.api_management_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TenantOrganizationRequest {

    @NotBlank
    @Size(max = 150)
    private String name;
}
