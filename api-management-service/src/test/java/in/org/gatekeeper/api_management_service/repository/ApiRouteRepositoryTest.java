package in.org.gatekeeper.api_management_service.repository;

import in.org.gatekeeper.api_management_service.entity.ApiRoute;
import in.org.gatekeeper.api_management_service.entity.Organization;
import in.org.gatekeeper.api_management_service.entity.OrganizationStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.LocalDateTime;
import java.util.Set;

import static in.org.gatekeeper.api_management_service.support.TestData.apiRoute;
import static in.org.gatekeeper.api_management_service.support.TestData.headerRule;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(showSql = false)
class ApiRouteRepositoryTest {

    @Autowired
    private ApiRouteRepository routes;
    @Autowired
    private OrganizationRepository organizations;
    @Autowired
    private EntityManager entityManager;

    private Organization acme;
    private Organization globex;

    private Organization organization(String slug, OrganizationStatus status) {
        return organizations.save(Organization.builder().name("Org " + slug).slug(slug).status(status)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
    }

    private ApiRoute save(Organization organization, String routeId, boolean active) {
        ApiRoute route = apiRoute(null, organization, routeId, active, 1L);
        return routes.save(route);
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    @BeforeEach
    void seed() {
        acme = organization("acme", OrganizationStatus.ACTIVE);
        globex = organization("globex", OrganizationStatus.ACTIVE);
    }

    @Test
    void existsByOrganizationIdAndRouteIdIsScopedToTheOrganization() {
        save(acme, "orders", true);

        assertThat(routes.existsByOrganizationIdAndRouteId(acme.getId(), "orders")).isTrue();
        assertThat(routes.existsByOrganizationIdAndRouteId(globex.getId(), "orders")).isFalse();
        assertThat(routes.existsByOrganizationIdAndRouteId(acme.getId(), "missing")).isFalse();
    }

    @Test
    void sameRouteIdCanExistInDifferentOrganizations() {
        save(acme, "orders", true);
        save(globex, "orders", true);

        assertThat(routes.findAllByOrganizationId(acme.getId())).hasSize(1);
        assertThat(routes.findAllByOrganizationId(globex.getId())).hasSize(1);
    }

    @Test
    void findByIdAndOrganizationIdDoesNotLeakAcrossOrganizations() {
        ApiRoute acmeRoute = save(acme, "orders", true);

        assertThat(routes.findByIdAndOrganizationId(acmeRoute.getId(), acme.getId())).isPresent();
        assertThat(routes.findByIdAndOrganizationId(acmeRoute.getId(), globex.getId())).isEmpty();
    }

    @Test
    void findAllByOrganizationIdReturnsActiveAndInactiveRoutes() {
        save(acme, "a", true);
        save(acme, "b", false);
        save(globex, "c", true);

        assertThat(routes.findAllByOrganizationId(acme.getId()))
                .extracting(ApiRoute::getRouteId).containsExactlyInAnyOrder("a", "b");
    }

    @Test
    void gatewayFeedContainsOnlyActiveRoutesOfActiveOrganizations() {
        Organization suspended = organization("suspended", OrganizationStatus.SUSPENDED);
        Organization pending = organization("pending", OrganizationStatus.PENDING);
        Organization deleted = organization("deleted", OrganizationStatus.DELETED);

        save(acme, "acme-active", true);
        save(acme, "acme-inactive", false);
        save(globex, "globex-active", true);
        save(suspended, "suspended-active", true);
        save(pending, "pending-active", true);
        save(deleted, "deleted-active", true);

        assertThat(routes.findAllByActiveTrueAndOrganizationStatus(OrganizationStatus.ACTIVE))
                .extracting(ApiRoute::getRouteId)
                .containsExactlyInAnyOrder("acme-active", "globex-active");
    }

    @Test
    void persistsAllowedMethodsAndLoadsThemEagerly() {
        ApiRoute route = apiRoute(null, acme, "orders", true, 1L);
        route.setAllowedMethods(Set.of("GET", "POST", "DELETE"));
        Long id = routes.save(route).getId();
        flushAndClear();

        ApiRoute loaded = routes.findById(id).orElseThrow();

        assertThat(loaded.getAllowedMethods()).containsExactlyInAnyOrder("GET", "POST", "DELETE");
    }

    @Test
    void savingRouteCascadesHeaderRules() {
        ApiRoute route = apiRoute(null, acme, "orders", true, 1L);
        route.getHeaderRules().add(headerRule(route, "REQUEST", "ADD", "X-A", "1"));
        route.getHeaderRules().add(headerRule(route, "RESPONSE", "REMOVE", "Server", null));
        Long id = routes.save(route).getId();
        flushAndClear();

        ApiRoute loaded = routes.findById(id).orElseThrow();

        assertThat(loaded.getHeaderRules()).hasSize(2);
        assertThat(loaded.getHeaderRules()).extracting("headerName").containsExactlyInAnyOrder("X-A", "Server");
        assertThat(loaded.getHeaderRules()).allSatisfy(rule -> assertThat(rule.getId()).isNotNull());
    }

    @Test
    void clearingRulesDeletesThemAsOrphans() {
        ApiRoute route = apiRoute(null, acme, "orders", true, 1L);
        route.getHeaderRules().add(headerRule(route, "REQUEST", "ADD", "X-A", "1"));
        Long id = routes.save(route).getId();
        flushAndClear();

        ApiRoute loaded = routes.findById(id).orElseThrow();
        loaded.getHeaderRules().clear();
        flushAndClear();

        assertThat(routes.findById(id).orElseThrow().getHeaderRules()).isEmpty();
        Number remaining = (Number) entityManager.createNativeQuery("select count(*) from route_header_rules")
                .getSingleResult();
        assertThat(remaining.longValue()).isZero();
    }

    @Test
    void deletingRouteRemovesItsRulesAndMethods() {
        ApiRoute route = apiRoute(null, acme, "orders", true, 1L);
        route.getHeaderRules().add(headerRule(route, "REQUEST", "ADD", "X-A", "1"));
        Long id = routes.save(route).getId();
        flushAndClear();

        routes.delete(routes.findById(id).orElseThrow());
        flushAndClear();

        assertThat(routes.findById(id)).isEmpty();
        assertThat(((Number) entityManager.createNativeQuery("select count(*) from route_header_rules")
                .getSingleResult()).longValue()).isZero();
        assertThat(((Number) entityManager.createNativeQuery("select count(*) from route_allowed_methods")
                .getSingleResult()).longValue()).isZero();
    }

    @Test
    void builderCreatedRouteStartsWithEmptyRuleAndMethodCollections() {
        ApiRoute built = ApiRoute.builder().organization(acme).build();

        assertThat(built.getHeaderRules()).isNotNull().isEmpty();
        assertThat(built.getAllowedMethods()).isNotNull().isEmpty();
    }
}
