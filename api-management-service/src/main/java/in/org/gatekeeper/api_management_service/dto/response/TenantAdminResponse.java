package in.org.gatekeeper.api_management_service.dto.response;

import in.org.gatekeeper.api_management_service.entity.ManagementUser;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TenantAdminResponse {

    private Long id;
    private Long organizationId;
    private String username;
    private boolean enabled;

    public static TenantAdminResponse from(ManagementUser user) {
        return new TenantAdminResponse(user.getId(), user.getOrganization().getId(), user.getUsername(),
                user.isEnabled());
    }
}
