package in.org.gatekeeper.api_management_service.controller;

import in.org.gatekeeper.api_management_service.config.SecurityConfig;
import in.org.gatekeeper.api_management_service.dto.response.RouteResponse;
import in.org.gatekeeper.api_management_service.service.RouteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static in.org.gatekeeper.api_management_service.support.Roles.gatewayService;
import static in.org.gatekeeper.api_management_service.support.Roles.platformAdmin;
import static in.org.gatekeeper.api_management_service.support.Roles.tenantAdmin;
import static in.org.gatekeeper.api_management_service.support.TestData.activeOrganization;
import static in.org.gatekeeper.api_management_service.support.TestData.apiRoute;
import static in.org.gatekeeper.api_management_service.support.TestData.headerRule;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GatewayConfigurationController.class)
@Import(SecurityConfig.class)
class GatewayConfigurationControllerTest {

    private static final String PATH = "/api/gateway/routes";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private RouteService routeService;

    @Test
    void returnsActiveRoutesOfAllTenantsWithTheirOrganizationIds() throws Exception {
        var acmeRoute = apiRoute(1L, activeOrganization(7L, "acme"), "orders", true, 2L);
        acmeRoute.getHeaderRules().add(headerRule(acmeRoute, "REQUEST", "ADD", "X-Tenant", "acme"));
        var globexRoute = apiRoute(2L, activeOrganization(8L, "globex"), "billing", true, 1L);
        when(routeService.findAllActiveForGateway())
                .thenReturn(List.of(RouteResponse.from(acmeRoute), RouteResponse.from(globexRoute)));

        mvc.perform(get(PATH).with(gatewayService()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].organizationId").value(7))
                .andExpect(jsonPath("$[0].pathPattern").value("/acme/orders"))
                .andExpect(jsonPath("$[0].targetBaseUrl").value("https://service.example.com"))
                .andExpect(jsonPath("$[0].headerRules[0].headerName").value("X-Tenant"))
                .andExpect(jsonPath("$[1].organizationId").value(8));
    }

    @Test
    void returnsEmptyArrayWhenNothingIsActive() throws Exception {
        when(routeService.findAllActiveForGateway()).thenReturn(List.of());

        mvc.perform(get(PATH).with(gatewayService()))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void rejectsAnonymousCallersWith401() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());

        verifyNoInteractions(routeService);
    }

    @Test
    void rejectsTenantAndPlatformAdminsWith403() throws Exception {
        mvc.perform(get(PATH).with(tenantAdmin())).andExpect(status().isForbidden());
        mvc.perform(get(PATH).with(platformAdmin())).andExpect(status().isForbidden());

        verifyNoInteractions(routeService);
    }

    @Test
    void doesNotAcceptWrites() throws Exception {
        mvc.perform(post(PATH).with(gatewayService())).andExpect(status().isMethodNotAllowed());
    }
}
