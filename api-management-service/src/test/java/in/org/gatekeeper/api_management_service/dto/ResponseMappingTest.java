package in.org.gatekeeper.api_management_service.dto;

import in.org.gatekeeper.api_management_service.dto.response.AuditRecordResponse;
import in.org.gatekeeper.api_management_service.dto.response.HeaderRuleResponse;
import in.org.gatekeeper.api_management_service.dto.response.OrganizationResponse;
import in.org.gatekeeper.api_management_service.dto.response.RouteResponse;
import in.org.gatekeeper.api_management_service.dto.response.RouteVersionResponse;
import in.org.gatekeeper.api_management_service.dto.response.TenantAdminResponse;
import in.org.gatekeeper.api_management_service.entity.ApiRoute;
import in.org.gatekeeper.api_management_service.entity.ControlPlaneAuditRecord;
import in.org.gatekeeper.api_management_service.entity.ManagementUser;
import in.org.gatekeeper.api_management_service.entity.Organization;
import in.org.gatekeeper.api_management_service.entity.OrganizationStatus;
import in.org.gatekeeper.api_management_service.entity.Role;
import in.org.gatekeeper.api_management_service.entity.RouteConfigurationVersion;
import in.org.gatekeeper.api_management_service.entity.RouteHeaderRule;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Set;

import static in.org.gatekeeper.api_management_service.support.TestData.activeOrganization;
import static in.org.gatekeeper.api_management_service.support.TestData.apiRoute;
import static in.org.gatekeeper.api_management_service.support.TestData.headerRule;
import static org.assertj.core.api.Assertions.assertThat;

class ResponseMappingTest {

    private final LocalDateTime now = LocalDateTime.of(2026, 10, 29, 12, 30);

    @Test
    void organizationResponseCopiesAllPublicFields() {
        Organization organization = activeOrganization(7L, "acme");

        OrganizationResponse response = OrganizationResponse.from(organization);

        assertThat(response.getId()).isEqualTo(7L);
        assertThat(response.getName()).isEqualTo("Org acme");
        assertThat(response.getSlug()).isEqualTo("acme");
        assertThat(response.getStatus()).isEqualTo(OrganizationStatus.ACTIVE);
        assertThat(response.getCreatedAt()).isEqualTo(organization.getCreatedAt());
        assertThat(response.getUpdatedAt()).isEqualTo(organization.getUpdatedAt());
    }

    @Test
    void tenantAdminResponseNeverExposesThePasswordHash() {
        Role role = new Role();
        role.setName("TENANT_ADMIN");
        ManagementUser user = ManagementUser.builder()
                .id(3L).organization(activeOrganization(7L, "acme")).role(role)
                .username("acme-admin").passwordHash("super-secret-hash").enabled(true)
                .build();

        TenantAdminResponse response = TenantAdminResponse.from(user);

        assertThat(response.getId()).isEqualTo(3L);
        assertThat(response.getOrganizationId()).isEqualTo(7L);
        assertThat(response.getUsername()).isEqualTo("acme-admin");
        assertThat(response.isEnabled()).isTrue();
        assertThat(TenantAdminResponse.class.getDeclaredFields())
                .extracting(java.lang.reflect.Field::getName)
                .noneMatch(name -> name.toLowerCase().contains("password"));
    }

    @Test
    void headerRuleResponseCopiesAllFields() {
        ApiRoute route = apiRoute(1L, activeOrganization(7L, "acme"), "orders", true, 1L);
        RouteHeaderRule rule = headerRule(route, "RESPONSE", "REMOVE", "Server", null);
        rule.setId(9L);

        HeaderRuleResponse response = HeaderRuleResponse.from(rule);

        assertThat(response.getId()).isEqualTo(9L);
        assertThat(response.getDirection()).isEqualTo("RESPONSE");
        assertThat(response.getAction()).isEqualTo("REMOVE");
        assertThat(response.getHeaderName()).isEqualTo("Server");
        assertThat(response.getHeaderValue()).isNull();
    }

    @Test
    void routeResponseCopiesRouteAndNestedRules() {
        Organization organization = activeOrganization(7L, "acme");
        ApiRoute route = apiRoute(10L, organization, "orders", true, 4L);
        route.setAllowedMethods(Set.of("GET", "POST"));
        route.setRequestsPerMinute(null);
        route.setIdempotencyEnabled(true);
        route.getHeaderRules().add(headerRule(route, "REQUEST", "ADD", "X-A", "1"));
        route.getHeaderRules().add(headerRule(route, "RESPONSE", "REMOVE", "Server", null));

        RouteResponse response = RouteResponse.from(route);

        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getOrganizationId()).isEqualTo(7L);
        assertThat(response.getRouteId()).isEqualTo("orders");
        assertThat(response.getName()).isEqualTo("Route orders");
        assertThat(response.getPathPattern()).isEqualTo("/acme/orders");
        assertThat(response.getTargetBaseUrl()).isEqualTo("https://service.example.com");
        assertThat(response.getTargetPathPrefix()).isEqualTo("/v1");
        assertThat(response.getRequestsPerMinute()).isNull();
        assertThat(response.getResponseTimeoutMs()).isEqualTo(5000);
        assertThat(response.isIdempotencyEnabled()).isTrue();
        assertThat(response.isActive()).isTrue();
        assertThat(response.getConfigurationVersion()).isEqualTo(4L);
        assertThat(response.getAllowedMethods()).containsExactlyInAnyOrder("GET", "POST");
        assertThat(response.getHeaderRules()).extracting(HeaderRuleResponse::getHeaderName)
                .containsExactly("X-A", "Server");
        assertThat(response.getCreatedAt()).isEqualTo(route.getCreatedAt());
        assertThat(response.getUpdatedAt()).isEqualTo(route.getUpdatedAt());
    }

    @Test
    void routeResponseOfRouteWithoutRulesHasEmptyRuleList() {
        ApiRoute route = apiRoute(10L, activeOrganization(7L, "acme"), "orders", false, 1L);

        assertThat(RouteResponse.from(route).getHeaderRules()).isEmpty();
    }

    @Test
    void routeVersionResponseKeepsSnapshotVerbatim() {
        RouteConfigurationVersion version = new RouteConfigurationVersion();
        version.setId(40L);
        version.setRouteId("orders");
        version.setVersionNumber(3L);
        version.setChangeType("UPDATED");
        version.setConfigurationSnapshot("{\"routeId\":\"orders\"}");
        version.setCreatedAt(now);

        RouteVersionResponse response = RouteVersionResponse.from(version);

        assertThat(response.getId()).isEqualTo(40L);
        assertThat(response.getRouteId()).isEqualTo("orders");
        assertThat(response.getVersionNumber()).isEqualTo(3L);
        assertThat(response.getChangeType()).isEqualTo("UPDATED");
        assertThat(response.getConfigurationSnapshot()).isEqualTo("{\"routeId\":\"orders\"}");
        assertThat(response.getCreatedAt()).isEqualTo(now);
    }

    @Test
    void auditRecordResponseCopiesAllFields() {
        ControlPlaneAuditRecord record = new ControlPlaneAuditRecord();
        record.setId(90L);
        record.setActor("acme-admin");
        record.setAction("UPDATE_ROUTE");
        record.setResourceType("API_ROUTE");
        record.setResourceId("orders");
        record.setCorrelationId("corr-1");
        record.setDurationMs(14L);
        record.setResult("SUCCESS");
        record.setDetails("Route configuration version 3 created");
        record.setCreatedAt(now);

        AuditRecordResponse response = AuditRecordResponse.from(record);

        assertThat(response.getId()).isEqualTo(90L);
        assertThat(response.getActor()).isEqualTo("acme-admin");
        assertThat(response.getAction()).isEqualTo("UPDATE_ROUTE");
        assertThat(response.getResourceType()).isEqualTo("API_ROUTE");
        assertThat(response.getResourceId()).isEqualTo("orders");
        assertThat(response.getCorrelationId()).isEqualTo("corr-1");
        assertThat(response.getDurationMs()).isEqualTo(14L);
        assertThat(response.getResult()).isEqualTo("SUCCESS");
        assertThat(response.getDetails()).isEqualTo("Route configuration version 3 created");
        assertThat(response.getCreatedAt()).isEqualTo(now);
    }
}
