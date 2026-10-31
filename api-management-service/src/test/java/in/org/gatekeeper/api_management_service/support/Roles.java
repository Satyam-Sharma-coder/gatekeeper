package in.org.gatekeeper.api_management_service.support;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

public final class Roles {

    private Roles() {
    }

    public static RequestPostProcessor platformAdmin() {
        return as("PLATFORM_ADMIN");
    }

    public static RequestPostProcessor tenantAdmin() {
        return as("TENANT_ADMIN");
    }

    public static RequestPostProcessor gatewayService() {
        return as("GATEWAY_SERVICE");
    }

    private static RequestPostProcessor as(String role) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
