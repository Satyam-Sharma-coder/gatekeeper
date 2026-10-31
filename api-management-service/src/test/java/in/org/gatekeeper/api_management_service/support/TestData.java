package in.org.gatekeeper.api_management_service.support;

import in.org.gatekeeper.api_management_service.dto.request.HeaderRuleRequest;
import in.org.gatekeeper.api_management_service.dto.request.RouteRequest;
import in.org.gatekeeper.api_management_service.entity.ApiRoute;
import in.org.gatekeeper.api_management_service.entity.Organization;
import in.org.gatekeeper.api_management_service.entity.OrganizationStatus;
import in.org.gatekeeper.api_management_service.entity.RouteHeaderRule;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class TestData {

    private TestData() {
    }

    public static Organization organization(Long id, String slug, OrganizationStatus status) {
        LocalDateTime now = LocalDateTime.of(2026, 10, 29, 10, 0);
        return Organization.builder()
                .id(id)
                .name("Org " + slug)
                .slug(slug)
                .status(status)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    public static Organization activeOrganization(Long id, String slug) {
        return organization(id, slug, OrganizationStatus.ACTIVE);
    }

    public static HeaderRuleRequest headerRuleRequest(String direction, String action, String name, String value) {
        HeaderRuleRequest rule = new HeaderRuleRequest();
        rule.setDirection(direction);
        rule.setAction(action);
        rule.setHeaderName(name);
        rule.setHeaderValue(value);
        return rule;
    }

    public static RouteRequest routeRequest(String routeId, String slug) {
        RouteRequest request = new RouteRequest();
        request.setRouteId(routeId);
        request.setName("Route " + routeId);
        request.setPathPattern("/" + slug + "/" + routeId);
        request.setTargetBaseUrl("https://service.example.com");
        request.setTargetPathPrefix("/v1");
        request.setRequestsPerMinute(600);
        request.setResponseTimeoutMs(5000);
        request.setIdempotencyEnabled(false);
        request.setActive(false);
        request.setAllowedMethods(new HashSet<String>(Set.of("get", "POST")));
        request.setHeaderRules(new ArrayList<HeaderRuleRequest>(
                List.of(headerRuleRequest("request", "add", "X-Tenant", slug))));
        return request;
    }

    public static ApiRoute apiRoute(Long id, Organization organization, String routeId, boolean active, long version) {
        LocalDateTime now = LocalDateTime.of(2026, 10, 29, 10, 0);
        ApiRoute route = new ApiRoute();
        route.setId(id);
        route.setOrganization(organization);
        route.setRouteId(routeId);
        route.setName("Route " + routeId);
        route.setPathPattern("/" + organization.getSlug() + "/" + routeId);
        route.setTargetBaseUrl("https://service.example.com");
        route.setTargetPathPrefix("/v1");
        route.setRequestsPerMinute(600);
        route.setResponseTimeoutMs(5000);
        route.setIdempotencyEnabled(false);
        route.setActive(active);
        route.setConfigurationVersion(version);
        route.setCreatedAt(now);
        route.setUpdatedAt(now);
        route.setAllowedMethods(new HashSet<String>(Set.of("GET")));
        return route;
    }

    public static RouteHeaderRule headerRule(ApiRoute route, String direction, String action, String name, String value) {
        RouteHeaderRule rule = new RouteHeaderRule();
        rule.setRoute(route);
        rule.setDirection(direction);
        rule.setAction(action);
        rule.setHeaderName(name);
        rule.setHeaderValue(value);
        rule.setCreatedAt(LocalDateTime.now());
        rule.setUpdatedAt(LocalDateTime.now());
        return rule;
    }
}
