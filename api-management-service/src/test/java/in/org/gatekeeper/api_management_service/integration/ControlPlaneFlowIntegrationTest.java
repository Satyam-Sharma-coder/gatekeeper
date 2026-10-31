package in.org.gatekeeper.api_management_service.integration;

import com.jayway.jsonpath.JsonPath;
import in.org.gatekeeper.api_management_service.entity.ManagementUser;
import in.org.gatekeeper.api_management_service.entity.Role;
import in.org.gatekeeper.api_management_service.exception.ApiException;
import in.org.gatekeeper.api_management_service.repository.ApiRouteRepository;
import in.org.gatekeeper.api_management_service.repository.ControlPlaneAuditRecordRepository;
import in.org.gatekeeper.api_management_service.repository.ManagementUserRepository;
import in.org.gatekeeper.api_management_service.repository.OrganizationRepository;
import in.org.gatekeeper.api_management_service.repository.RoleRepository;
import in.org.gatekeeper.api_management_service.repository.RouteConfigurationVersionRepository;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

@SpringBootTest
@AutoConfigureMockMvc
class ControlPlaneFlowIntegrationTest {

    private static final String PLATFORM_PASSWORD = "platform-pass-1";
    private static final String GATEWAY_PASSWORD = "gateway-pass-1";
    private static final String TENANT_PASSWORD = "tenant-pass-1";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private RoleRepository roles;
    @Autowired
    private ManagementUserRepository users;
    @Autowired
    private OrganizationRepository organizations;
    @Autowired
    private ApiRouteRepository routes;
    @Autowired
    private RouteConfigurationVersionRepository versions;
    @Autowired
    private ControlPlaneAuditRecordRepository audits;

    private String platformToken;
    private String gatewayToken;

    private record Outcome(int status, String body) {
        Object read(String path) {
            return JsonPath.read(body, path);
        }

        long readLong(String path) {
            return ((Number) read(path)).longValue();
        }

        @SuppressWarnings("unchecked")
        List<Object> list(String path) {
            return (List<Object>) read(path);
        }

        int size(String path) {
            return ((List<?>) read(path)).size();
        }
    }

    private record Tenant(long organizationId, String slug, String username, String token) {
    }

    @BeforeEach
    void resetDatabaseAndSeedAccounts() throws Exception {
        audits.deleteAll();
        versions.deleteAll();
        routes.deleteAll();
        users.deleteAll();
        organizations.deleteAll();
        roles.deleteAll();

        Role platformRole = role("PLATFORM_ADMIN");
        role("TENANT_ADMIN");
        Role gatewayRole = role("GATEWAY_SERVICE");
        account("platform-admin", PLATFORM_PASSWORD, platformRole);
        account("gateway-service", GATEWAY_PASSWORD, gatewayRole);

        platformToken = login("platform-admin", PLATFORM_PASSWORD);
        gatewayToken = login("gateway-service", GATEWAY_PASSWORD);
    }

    private Role role(String name) {
        Role role = new Role();
        role.setName(name);
        return roles.save(role);
    }

    private void account(String username, String password, Role role) {
        users.save(ManagementUser.builder().username(username).passwordHash(passwordEncoder.encode(password))
                .enabled(true).role(role).createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
    }

    private Outcome call(MockHttpServletRequestBuilder request) throws Exception {
        try {
            MvcResult result = mvc.perform(request).andReturn();
            return new Outcome(result.getResponse().getStatus(), result.getResponse().getContentAsString());
        } catch (ServletException exception) {
            Throwable cause = exception;
            while (cause != null && !(cause instanceof ApiException)) {
                cause = cause.getCause();
            }
            if (cause == null) {
                throw exception;
            }
            return new Outcome(((ApiException) cause).getStatus().value(), cause.getMessage());
        }
    }

    private MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder request, String token) {
        return request.header("Authorization", "Bearer " + token);
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String token, String body) {
        return authed(request, token).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private String login(String username, String password) throws Exception {
        Outcome outcome = loginOutcome(username, password);
        assertThat(outcome.status()).as("login of %s", username).isEqualTo(200);
        return (String) outcome.read("$.token");
    }

    private Outcome loginOutcome(String username, String password) throws Exception {
        return call(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"));
    }

    private long createOrganization(String name, String slug) throws Exception {
        Outcome created = call(json(post("/api/platform/organizations"), platformToken,
                "{\"name\":\"" + name + "\",\"slug\":\"" + slug + "\"}"));
        assertThat(created.status()).isEqualTo(201);
        return created.readLong("$.id");
    }

    private void setStatus(long organizationId, String status) throws Exception {
        Outcome outcome = call(json(patch("/api/platform/organizations/" + organizationId + "/status"),
                platformToken, "{\"status\":\"" + status + "\"}"));
        assertThat(outcome.status()).isEqualTo(200);
    }

    private Outcome createTenantAdmin(long organizationId, String username, String password) throws Exception {
        return call(json(post("/api/platform/organizations/" + organizationId + "/tenant-admin"), platformToken,
                "{\"username\":\"" + username + "\",\"password\":\"" + password + "\",\"enabled\":true}"));
    }

    private Tenant activeTenant(String slug) throws Exception {
        long id = createOrganization("Org " + slug, slug);
        assertThat(createTenantAdmin(id, slug + "-admin", TENANT_PASSWORD).status()).isEqualTo(201);
        setStatus(id, "ACTIVE");
        return new Tenant(id, slug, slug + "-admin", login(slug + "-admin", TENANT_PASSWORD));
    }

    private static String routeJson(String routeId, String slug, String name, boolean active, String headerName) {
        return """
                {
                  "routeId": "%s",
                  "name": "%s",
                  "pathPattern": "/%s/%s",
                  "targetBaseUrl": "https://service.example.com",
                  "targetPathPrefix": "/v1",
                  "requestsPerMinute": 600,
                  "responseTimeoutMs": 5000,
                  "idempotencyEnabled": false,
                  "active": %s,
                  "allowedMethods": ["get", "POST"],
                  "headerRules": [
                    {"direction": "request", "action": "add", "headerName": "%s", "headerValue": "v"}
                  ]
                }
                """.formatted(routeId, name, slug, routeId, active, headerName);
    }

    private Outcome createRoute(Tenant tenant, String routeId, boolean active) throws Exception {
        return call(json(post("/api/tenant/routes"), tenant.token(),
                routeJson(routeId, tenant.slug(), "Route " + routeId, active, "X-Tenant")));
    }

    @Nested
    class Onboarding {

        @Test
        void tenantCannotSignInUntilTheOrganizationIsActivated() throws Exception {
            long id = createOrganization("Acme Corp", "acme");
            assertThat(createTenantAdmin(id, "acme-admin", TENANT_PASSWORD).status()).isEqualTo(201);

            assertThat(loginOutcome("acme-admin", TENANT_PASSWORD).status()).isEqualTo(403);

            setStatus(id, "ACTIVE");

            Outcome signedIn = loginOutcome("acme-admin", TENANT_PASSWORD);
            assertThat(signedIn.status()).isEqualTo(200);
            assertThat(signedIn.read("$.role")).isEqualTo("TENANT_ADMIN");
            assertThat(signedIn.readLong("$.organizationId")).isEqualTo(id);
            assertThat(signedIn.readLong("$.expiresInSeconds")).isEqualTo(3600);
        }

        @Test
        void newOrganizationStartsPendingAndAppearsInTheList() throws Exception {
            createOrganization("Acme Corp", "acme");

            Outcome list = call(authed(get("/api/platform/organizations"), platformToken));

            assertThat(list.status()).isEqualTo(200);
            assertThat(list.size("$")).isEqualTo(1);
            assertThat(list.read("$[0].status")).isEqualTo("PENDING");
            assertThat(list.read("$[0].slug")).isEqualTo("acme");
        }

        @Test
        void organizationNameAndSlugMustBeUniqueIgnoringCase() throws Exception {
            createOrganization("Acme Corp", "acme");

            assertThat(call(json(post("/api/platform/organizations"), platformToken,
                    "{\"name\":\"ACME CORP\",\"slug\":\"other\"}")).status()).isEqualTo(409);
            assertThat(call(json(post("/api/platform/organizations"), platformToken,
                    "{\"name\":\"Other\",\"slug\":\"acme\"}")).status()).isEqualTo(409);
        }

        @Test
        void statusCannotBeSetToDeletedThroughThePatchEndpoint() throws Exception {
            long id = createOrganization("Acme Corp", "acme");

            Outcome outcome = call(json(patch("/api/platform/organizations/" + id + "/status"), platformToken,
                    "{\"status\":\"DELETED\"}"));

            assertThat(outcome.status()).isEqualTo(400);
        }

        @Test
        void anOrganizationHasAtMostOneTenantAdminAndUsernamesAreUnique() throws Exception {
            long first = createOrganization("Acme Corp", "acme");
            long second = createOrganization("Globex", "globex");
            assertThat(createTenantAdmin(first, "acme-admin", TENANT_PASSWORD).status()).isEqualTo(201);

            assertThat(createTenantAdmin(first, "another-admin", TENANT_PASSWORD).status()).isEqualTo(409);
            assertThat(createTenantAdmin(second, "acme-admin", TENANT_PASSWORD).status()).isEqualTo(409);
            assertThat(createTenantAdmin(second, "platform-admin", TENANT_PASSWORD).status()).isEqualTo(409);
        }

        @Test
        void tenantAdminPasswordChangeDisableAndDeleteTakeEffectOnLogin() throws Exception {
            long id = createOrganization("Acme Corp", "acme");
            createTenantAdmin(id, "acme-admin", TENANT_PASSWORD);
            setStatus(id, "ACTIVE");
            String path = "/api/platform/organizations/" + id + "/tenant-admin";

            Outcome changed = call(json(put(path), platformToken,
                    "{\"username\":\"acme-admin\",\"password\":\"brand-new-pass\",\"enabled\":true}"));
            assertThat(changed.status()).isEqualTo(200);
            assertThat(loginOutcome("acme-admin", TENANT_PASSWORD).status()).isEqualTo(401);
            assertThat(loginOutcome("acme-admin", "brand-new-pass").status()).isEqualTo(200);

            call(json(put(path), platformToken,
                    "{\"username\":\"acme-admin\",\"password\":\"brand-new-pass\",\"enabled\":false}"));
            assertThat(loginOutcome("acme-admin", "brand-new-pass").status()).isEqualTo(401);

            assertThat(call(authed(delete(path), platformToken)).status()).isEqualTo(204);
            assertThat(call(authed(get(path), platformToken)).status()).isEqualTo(404);
            assertThat(loginOutcome("acme-admin", "brand-new-pass").status()).isEqualTo(401);
        }

        @Test
        void loginRejectsBadCredentialsWithoutSayingWhichPartWasWrong() throws Exception {
            long id = createOrganization("Acme Corp", "acme");
            createTenantAdmin(id, "acme-admin", TENANT_PASSWORD);
            setStatus(id, "ACTIVE");

            Outcome wrongPassword = loginOutcome("acme-admin", "nope");
            Outcome unknownUser = loginOutcome("ghost", "nope");

            assertThat(wrongPassword.status()).isEqualTo(401);
            assertThat(unknownUser.status()).isEqualTo(401);
            assertThat(wrongPassword.body()).isEqualTo(unknownUser.body());
            assertThat(call(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}")).status())
                    .isEqualTo(400);
        }
    }

    @Nested
    class RouteLifecycle {

        @Test
        void createActivateUpdateDeactivateAndDeleteWriteVersionsAndFeedChanges() throws Exception {
            Tenant acme = activeTenant("acme");

            Outcome created = createRoute(acme, "orders", false);
            assertThat(created.status()).isEqualTo(201);
            assertThat(created.readLong("$.configurationVersion")).isEqualTo(1);
            assertThat(created.read("$.active")).isEqualTo(false);
            assertThat(created.readLong("$.organizationId")).isEqualTo(acme.organizationId());
            assertThat(created.list("$.allowedMethods")).containsExactlyInAnyOrder("GET", "POST");
            assertThat(created.read("$.headerRules[0].direction")).isEqualTo("REQUEST");
            assertThat(created.read("$.headerRules[0].action")).isEqualTo("ADD");
            long id = created.readLong("$.id");

            assertThat(call(authed(get("/api/gateway/routes"), gatewayToken)).size("$")).isZero();

            Outcome activated = call(json(patch("/api/tenant/routes/" + id + "/status"), acme.token(), "{\"active\":true}"));
            assertThat(activated.status()).isEqualTo(200);
            assertThat(activated.readLong("$.configurationVersion")).isEqualTo(2);
            Outcome feed = call(authed(get("/api/gateway/routes"), gatewayToken));
            assertThat(feed.size("$")).isEqualTo(1);
            assertThat(feed.readLong("$[0].organizationId")).isEqualTo(acme.organizationId());
            assertThat(feed.read("$[0].routeId")).isEqualTo("orders");

            Outcome updated = call(json(put("/api/tenant/routes/" + id), acme.token(),
                    routeJson("orders", "acme", "Orders renamed", true, "X-Replaced")));
            assertThat(updated.status()).isEqualTo(200);
            assertThat(updated.readLong("$.configurationVersion")).isEqualTo(3);
            assertThat(updated.read("$.name")).isEqualTo("Orders renamed");
            assertThat(updated.size("$.headerRules")).isEqualTo(1);
            assertThat(updated.read("$.headerRules[0].headerName")).isEqualTo("X-Replaced");

            Outcome deactivated = call(json(patch("/api/tenant/routes/" + id + "/status"), acme.token(),
                    "{\"active\":false}"));
            assertThat(deactivated.readLong("$.configurationVersion")).isEqualTo(4);
            assertThat(call(authed(get("/api/gateway/routes"), gatewayToken)).size("$")).isZero();

            assertThat(call(authed(delete("/api/tenant/routes/" + id), acme.token())).status()).isEqualTo(204);
            assertThat(call(authed(get("/api/tenant/routes/" + id), acme.token())).status()).isEqualTo(404);
            assertThat(call(authed(get("/api/tenant/routes"), acme.token())).size("$")).isZero();

            Outcome history = call(authed(get("/api/tenant/route-versions/orders"), acme.token()));
            assertThat(history.status()).isEqualTo(200);
            assertThat(history.size("$")).isEqualTo(5);
            assertThat(history.list("$[*].versionNumber")).containsExactly(5, 4, 3, 2, 1);
            assertThat(history.list("$[*].changeType"))
                    .containsExactly("DELETED", "DEACTIVATED", "UPDATED", "ACTIVATED", "CREATED");
            String firstSnapshot = (String) history.read("$[4].configurationSnapshot");
            assertThat((Object) JsonPath.read(firstSnapshot, "$.name")).isEqualTo("Route orders");
            assertThat((Object) JsonPath.read(firstSnapshot, "$.active")).isEqualTo(false);
            String updatedSnapshot = (String) history.read("$[2].configurationSnapshot");
            assertThat((Object) JsonPath.read(updatedSnapshot, "$.name")).isEqualTo("Orders renamed");
        }

        @Test
        void auditLogRecordsEachChangeWithActorAndCorrelationId() throws Exception {
            Tenant acme = activeTenant("acme");

            Outcome created = call(json(post("/api/tenant/routes"), acme.token(),
                    routeJson("orders", "acme", "Orders", false, "X-Tenant")).header("X-Correlation-ID", "corr-42"));
            long id = created.readLong("$.id");
            call(json(patch("/api/tenant/routes/" + id + "/status"), acme.token(), "{\"active\":true}"));

            Outcome audit = call(authed(get("/api/tenant/audit-records"), acme.token()));

            assertThat(audit.status()).isEqualTo(200);
            List<Object> actions = audit.list("$[*].action");
            assertThat(actions).contains("CREATE_ROUTE", "ACTIVATE_ROUTE", "CREATE_ORGANIZATION",
                    "CREATE_TENANT_ADMIN", "CHANGE_ORGANIZATION_STATUS");
            assertThat(audit.read("$[?(@.action=='CREATE_ROUTE')].actor")).isEqualTo(List.of("acme-admin"));
            assertThat(audit.read("$[?(@.action=='CREATE_ROUTE')].correlationId")).isEqualTo(List.of("corr-42"));
            assertThat(audit.read("$[?(@.action=='CREATE_ROUTE')].resourceId")).isEqualTo(List.of("orders"));
            assertThat(audit.read("$[?(@.action=='CREATE_ORGANIZATION')].actor")).isEqualTo(List.of("platform-admin"));
            assertThat(audit.list("$[*].result")).containsOnly("SUCCESS");
            assertThat(audit.readLong("$[0].durationMs")).isGreaterThanOrEqualTo(0);
        }

        @Test
        void duplicateRouteIdIsRejected() throws Exception {
            Tenant acme = activeTenant("acme");
            assertThat(createRoute(acme, "orders", false).status()).isEqualTo(201);

            assertThat(createRoute(acme, "orders", false).status()).isEqualTo(409);
            assertThat(call(authed(get("/api/tenant/routes"), acme.token())).size("$")).isEqualTo(1);
        }

        @Test
        void invalidRoutesAreRejectedWithoutSideEffects() throws Exception {
            Tenant acme = activeTenant("acme");

            Outcome wrongPrefix = call(json(post("/api/tenant/routes"), acme.token(),
                    routeJson("orders", "globex", "Orders", false, "X-Tenant")));
            Outcome badUrl = call(json(post("/api/tenant/routes"), acme.token(),
                    routeJson("orders", "acme", "Orders", false, "X-Tenant")
                            .replace("https://service.example.com", "ftp://files.example.com")));
            Outcome addWithoutValue = call(json(post("/api/tenant/routes"), acme.token(),
                    routeJson("orders", "acme", "Orders", false, "X-Tenant")
                            .replace("\"headerValue\": \"v\"", "\"headerValue\": \"\"")));
            Outcome emptyBody = call(json(post("/api/tenant/routes"), acme.token(), "{}"));

            assertThat(wrongPrefix.status()).isEqualTo(400);
            assertThat(badUrl.status()).isEqualTo(400);
            assertThat(addWithoutValue.status()).isEqualTo(400);
            assertThat(emptyBody.status()).isEqualTo(400);
            assertThat(call(authed(get("/api/tenant/routes"), acme.token())).size("$")).isZero();
            assertThat(call(authed(get("/api/tenant/route-versions/orders"), acme.token())).size("$")).isZero();
        }

        @Test
        void routeIdCannotBeChangedByUpdate() throws Exception {
            Tenant acme = activeTenant("acme");
            long id = createRoute(acme, "orders", false).readLong("$.id");

            Outcome outcome = call(json(put("/api/tenant/routes/" + id), acme.token(),
                    routeJson("renamed", "acme", "Orders", false, "X-Tenant")));

            assertThat(outcome.status()).isEqualTo(400);
            assertThat(call(authed(get("/api/tenant/routes/" + id), acme.token())).readLong("$.configurationVersion"))
                    .isEqualTo(1);
        }

        @Test
        void tenantCanRenameItsOrganizationButNotChangeSlugOrStatus() throws Exception {
            Tenant acme = activeTenant("acme");

            Outcome renamed = call(json(put("/api/tenant/organization"), acme.token(),
                    "{\"name\":\"Acme International\",\"slug\":\"hijacked\",\"status\":\"SUSPENDED\"}"));

            assertThat(renamed.status()).isEqualTo(200);
            assertThat(renamed.read("$.name")).isEqualTo("Acme International");
            assertThat(renamed.read("$.slug")).isEqualTo("acme");
            assertThat(renamed.read("$.status")).isEqualTo("ACTIVE");
            assertThat(call(authed(get("/api/tenant/organization"), acme.token())).read("$.name"))
                    .isEqualTo("Acme International");
        }

        @Test
        void tenantCannotTakeAnotherOrganizationsName() throws Exception {
            Tenant acme = activeTenant("acme");
            activeTenant("globex");

            Outcome outcome = call(json(put("/api/tenant/organization"), acme.token(), "{\"name\":\"Org globex\"}"));

            assertThat(outcome.status()).isEqualTo(409);
        }
    }

    @Nested
    class TenantIsolation {

        @Test
        void aTenantNeverSeesOrTouchesAnotherTenantsRoutes() throws Exception {
            Tenant acme = activeTenant("acme");
            Tenant globex = activeTenant("globex");
            long acmeRouteId = createRoute(acme, "orders", true).readLong("$.id");

            assertThat(call(authed(get("/api/tenant/routes"), globex.token())).size("$")).isZero();
            assertThat(call(authed(get("/api/tenant/routes/" + acmeRouteId), globex.token())).status()).isEqualTo(404);
            assertThat(call(json(put("/api/tenant/routes/" + acmeRouteId), globex.token(),
                    routeJson("orders", "globex", "Stolen", true, "X-A"))).status()).isEqualTo(404);
            assertThat(call(json(patch("/api/tenant/routes/" + acmeRouteId + "/status"), globex.token(),
                    "{\"active\":false}")).status()).isEqualTo(404);
            assertThat(call(authed(delete("/api/tenant/routes/" + acmeRouteId), globex.token())).status())
                    .isEqualTo(404);
            assertThat(call(authed(get("/api/tenant/route-versions/orders"), globex.token())).size("$")).isZero();

            Outcome untouched = call(authed(get("/api/tenant/routes/" + acmeRouteId), acme.token()));
            assertThat(untouched.status()).isEqualTo(200);
            assertThat(untouched.read("$.name")).isEqualTo("Route orders");
            assertThat(untouched.readLong("$.configurationVersion")).isEqualTo(1);
            assertThat(untouched.read("$.active")).isEqualTo(true);
        }

        @Test
        void routeIdsAreUniquePerOrganizationNotGlobally() throws Exception {
            Tenant acme = activeTenant("acme");
            Tenant globex = activeTenant("globex");

            assertThat(createRoute(acme, "orders", false).status()).isEqualTo(201);
            assertThat(createRoute(globex, "orders", false).status()).isEqualTo(201);

            assertThat(call(authed(get("/api/tenant/route-versions/orders"), acme.token())).size("$")).isEqualTo(1);
            assertThat(call(authed(get("/api/tenant/route-versions/orders"), globex.token())).size("$")).isEqualTo(1);
        }

        @Test
        void aTenantCannotCreateRoutesUnderAnotherTenantsPrefix() throws Exception {
            activeTenant("acme");
            Tenant globex = activeTenant("globex");

            Outcome outcome = call(json(post("/api/tenant/routes"), globex.token(),
                    routeJson("orders", "acme", "Squatter", false, "X-A")));

            assertThat(outcome.status()).isEqualTo(400);
        }

        @Test
        void auditLogsAreSeparatedPerOrganization() throws Exception {
            Tenant acme = activeTenant("acme");
            Tenant globex = activeTenant("globex");
            createRoute(acme, "acme-only-route", false);

            Outcome globexAudit = call(authed(get("/api/tenant/audit-records"), globex.token()));

            assertThat(globexAudit.status()).isEqualTo(200);
            assertThat(globexAudit.list("$[*].resourceId")).doesNotContain("acme-only-route");
            assertThat(globexAudit.list("$[*].action")).doesNotContain("CREATE_ROUTE");
        }

        @Test
        void gatewayFeedMergesAllActiveTenants() throws Exception {
            Tenant acme = activeTenant("acme");
            Tenant globex = activeTenant("globex");
            createRoute(acme, "a1", true);
            createRoute(acme, "a2", false);
            createRoute(globex, "g1", true);

            Outcome feed = call(authed(get("/api/gateway/routes"), gatewayToken));

            assertThat(feed.list("$[*].routeId")).containsExactlyInAnyOrder("a1", "g1");
        }
    }

    @Nested
    class OrganizationLifecycleEffects {

        @Test
        void suspendingAnOrganizationStopsItsTenantAndItsRoutesUntilReactivated() throws Exception {
            Tenant acme = activeTenant("acme");
            createRoute(acme, "orders", true);
            assertThat(call(authed(get("/api/gateway/routes"), gatewayToken)).size("$")).isEqualTo(1);

            setStatus(acme.organizationId(), "SUSPENDED");

            assertThat(call(authed(get("/api/gateway/routes"), gatewayToken)).size("$")).isZero();
            assertThat(call(authed(get("/api/tenant/routes"), acme.token())).status()).isEqualTo(403);
            assertThat(call(authed(get("/api/tenant/organization"), acme.token())).status()).isEqualTo(403);
            assertThat(loginOutcome(acme.username(), TENANT_PASSWORD).status()).isEqualTo(403);

            setStatus(acme.organizationId(), "ACTIVE");

            assertThat(call(authed(get("/api/tenant/routes"), acme.token())).size("$")).isEqualTo(1);
            assertThat(call(authed(get("/api/gateway/routes"), gatewayToken)).size("$")).isEqualTo(1);
        }

        @Test
        void deletingAnOrganizationHidesItAndLocksOutItsTenant() throws Exception {
            Tenant acme = activeTenant("acme");
            createRoute(acme, "orders", true);

            assertThat(call(authed(delete("/api/platform/organizations/" + acme.organizationId()), platformToken))
                    .status()).isEqualTo(204);

            assertThat(call(authed(get("/api/platform/organizations"), platformToken)).size("$")).isZero();
            assertThat(call(authed(get("/api/platform/organizations/" + acme.organizationId()), platformToken))
                    .status()).isEqualTo(404);
            assertThat(call(authed(get("/api/gateway/routes"), gatewayToken)).size("$")).isZero();
            assertThat(call(authed(get("/api/tenant/routes"), acme.token())).status()).isEqualTo(403);
            assertThat(loginOutcome(acme.username(), TENANT_PASSWORD).status()).isEqualTo(403);
            assertThat(organizations.findById(acme.organizationId())).isPresent();
        }

        @Test
        void pendingOrganizationsDoNotPublishRoutes() throws Exception {
            Tenant acme = activeTenant("acme");
            createRoute(acme, "orders", true);

            setStatus(acme.organizationId(), "PENDING");

            assertThat(call(authed(get("/api/gateway/routes"), gatewayToken)).size("$")).isZero();
        }
    }

    @Nested
    class AccessControl {

        @Test
        void rolesCannotCrossIntoEachOthersAreas() throws Exception {
            Tenant acme = activeTenant("acme");

            assertThat(call(authed(get("/api/platform/organizations"), acme.token())).status()).isEqualTo(403);
            assertThat(call(authed(get("/api/gateway/routes"), acme.token())).status()).isEqualTo(403);
            assertThat(call(authed(get("/api/tenant/routes"), platformToken)).status()).isEqualTo(403);
            assertThat(call(authed(get("/api/gateway/routes"), platformToken)).status()).isEqualTo(403);
            assertThat(call(authed(get("/api/tenant/routes"), gatewayToken)).status()).isEqualTo(403);
            assertThat(call(authed(get("/api/platform/organizations"), gatewayToken)).status()).isEqualTo(403);
        }

        @Test
        void anonymousCallersAreRejectedEverywhereExceptLoginAndHealth() throws Exception {
            assertThat(call(get("/api/platform/organizations")).status()).isEqualTo(401);
            assertThat(call(get("/api/tenant/routes")).status()).isEqualTo(401);
            assertThat(call(get("/api/gateway/routes")).status()).isEqualTo(401);
            assertThat(call(get("/health")).status()).isEqualTo(200);
        }

        @Test
        void healthReportsTheRealDatabaseAsUp() throws Exception {
            Outcome health = call(get("/health"));

            assertThat(health.read("$.serviceStatus")).isEqualTo("UP");
            assertThat(health.read("$.dbStatus")).isEqualTo("UP");
        }

        @Test
        void platformAdminCannotUseTenantEndpointsEvenWithAValidTokenBecauseItHasNoOrganization() throws Exception {
            assertThat(call(authed(get("/api/tenant/organization"), platformToken)).status()).isEqualTo(403);
        }
    }
}
