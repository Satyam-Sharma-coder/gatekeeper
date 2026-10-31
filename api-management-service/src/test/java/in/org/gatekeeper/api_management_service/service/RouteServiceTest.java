package in.org.gatekeeper.api_management_service.service;

import in.org.gatekeeper.api_management_service.dto.request.RouteRequest;
import in.org.gatekeeper.api_management_service.dto.request.RouteStatusRequest;
import in.org.gatekeeper.api_management_service.dto.response.AuditRecordResponse;
import in.org.gatekeeper.api_management_service.dto.response.RouteResponse;
import in.org.gatekeeper.api_management_service.dto.response.RouteVersionResponse;
import in.org.gatekeeper.api_management_service.entity.ApiRoute;
import in.org.gatekeeper.api_management_service.entity.ControlPlaneAuditRecord;
import in.org.gatekeeper.api_management_service.entity.Organization;
import in.org.gatekeeper.api_management_service.entity.OrganizationStatus;
import in.org.gatekeeper.api_management_service.entity.RouteConfigurationVersion;
import in.org.gatekeeper.api_management_service.exception.ApiException;
import in.org.gatekeeper.api_management_service.repository.ApiRouteRepository;
import in.org.gatekeeper.api_management_service.repository.ControlPlaneAuditRecordRepository;
import in.org.gatekeeper.api_management_service.repository.RouteConfigurationVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static in.org.gatekeeper.api_management_service.support.ApiExceptionAssertions.assertApiException;
import static in.org.gatekeeper.api_management_service.support.TestData.activeOrganization;
import static in.org.gatekeeper.api_management_service.support.TestData.apiRoute;
import static in.org.gatekeeper.api_management_service.support.TestData.headerRule;
import static in.org.gatekeeper.api_management_service.support.TestData.headerRuleRequest;
import static in.org.gatekeeper.api_management_service.support.TestData.routeRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RouteServiceTest {

    private static final Long ORG_ID = 7L;

    @Mock
    private ApiRouteRepository apiRouteRepository;
    @Mock
    private RouteConfigurationVersionRepository versionRepository;
    @Mock
    private ControlPlaneAuditRecordRepository auditRecordRepository;
    @Mock
    private TenantContext tenantContext;
    @Mock
    private AuditService auditService;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private Organization organization;
    private RouteService service;

    @BeforeEach
    void setUp() {
        organization = activeOrganization(ORG_ID, "acme");
        lenient().when(tenantContext.getOrganization()).thenReturn(organization);
        service = new RouteService(apiRouteRepository, versionRepository, auditRecordRepository,
                tenantContext, auditService, objectMapper);
    }

    private RouteConfigurationVersion capturedVersion() {
        ArgumentCaptor<RouteConfigurationVersion> captor = ArgumentCaptor.forClass(RouteConfigurationVersion.class);
        verify(versionRepository).save(captor.capture());
        return captor.getValue();
    }

    @Nested
    class Create {

        @Test
        void savesRouteAtVersionOneWithNormalizedMethodsAndRules() {
            when(apiRouteRepository.existsByOrganizationIdAndRouteId(ORG_ID, "orders")).thenReturn(false);

            RouteResponse response = service.create(routeRequest("orders", "acme"));

            ArgumentCaptor<ApiRoute> captor = ArgumentCaptor.forClass(ApiRoute.class);
            verify(apiRouteRepository).save(captor.capture());
            ApiRoute saved = captor.getValue();
            assertThat(saved.getOrganization()).isSameAs(organization);
            assertThat(saved.getConfigurationVersion()).isEqualTo(1L);
            assertThat(saved.getRouteId()).isEqualTo("orders");
            assertThat(saved.getAllowedMethods()).containsExactlyInAnyOrder("GET", "POST");
            assertThat(saved.isActive()).isFalse();
            assertThat(saved.getCreatedAt()).isNotNull();
            assertThat(saved.getUpdatedAt()).isNotNull();
            assertThat(saved.getHeaderRules()).hasSize(1);
            assertThat(saved.getHeaderRules().get(0).getDirection()).isEqualTo("REQUEST");
            assertThat(saved.getHeaderRules().get(0).getAction()).isEqualTo("ADD");
            assertThat(saved.getHeaderRules().get(0).getHeaderName()).isEqualTo("X-Tenant");
            assertThat(saved.getHeaderRules().get(0).getRoute()).isSameAs(saved);

            assertThat(response.getRouteId()).isEqualTo("orders");
            assertThat(response.getConfigurationVersion()).isEqualTo(1L);
            assertThat(response.getOrganizationId()).isEqualTo(ORG_ID);
        }

        @Test
        void writesCreatedVersionSnapshotAndAuditRecord() {
            service.create(routeRequest("orders", "acme"));

            RouteConfigurationVersion version = capturedVersion();
            assertThat(version.getOrganization()).isSameAs(organization);
            assertThat(version.getRouteId()).isEqualTo("orders");
            assertThat(version.getVersionNumber()).isEqualTo(1L);
            assertThat(version.getChangeType()).isEqualTo("CREATED");
            assertThat(version.getConfigurationSnapshot()).contains("\"routeId\":\"orders\"");
            assertThat(version.getCreatedAt()).isNotNull();

            verify(auditService).record(eq(organization), eq("CREATE_ROUTE"), eq("API_ROUTE"), eq("orders"),
                    anyLong(), eq("Route configuration version 1 created"));
        }

        @Test
        void rejectsDuplicateRouteIdWithinTheOrganization() {
            when(apiRouteRepository.existsByOrganizationIdAndRouteId(ORG_ID, "orders")).thenReturn(true);

            assertApiException(() -> service.create(routeRequest("orders", "acme")), HttpStatus.CONFLICT);

            verify(apiRouteRepository, never()).save(any());
            verifyNoInteractions(versionRepository, auditService);
        }

        @Test
        void acceptsRemoveRuleWithoutValue() {
            RouteRequest request = routeRequest("orders", "acme");
            request.getHeaderRules().add(headerRuleRequest("RESPONSE", "REMOVE", "Server", null));

            service.create(request);

            ArgumentCaptor<ApiRoute> captor = ArgumentCaptor.forClass(ApiRoute.class);
            verify(apiRouteRepository).save(captor.capture());
            assertThat(captor.getValue().getHeaderRules()).hasSize(2);
            assertThat(captor.getValue().getHeaderRules().get(1).getHeaderValue()).isNull();
        }

        @Test
        void acceptsRequestWithNoHeaderRules() {
            RouteRequest request = routeRequest("orders", "acme");
            request.getHeaderRules().clear();

            service.create(request);

            ArgumentCaptor<ApiRoute> captor = ArgumentCaptor.forClass(ApiRoute.class);
            verify(apiRouteRepository).save(captor.capture());
            assertThat(captor.getValue().getHeaderRules()).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(strings = {"/acme", "/acmecorp/orders", "/globex/orders", "acme/orders", "/ACME/orders", "/"})
        void rejectsPathPatternOutsideTheTenantPrefix(String pathPattern) {
            RouteRequest request = routeRequest("orders", "acme");
            request.setPathPattern(pathPattern);

            assertApiException(() -> service.create(request), HttpStatus.BAD_REQUEST, "/acme/");

            verify(apiRouteRepository, never()).save(any());
        }

        @Test
        void rejectsTargetPathPrefixWithoutLeadingSlash() {
            RouteRequest request = routeRequest("orders", "acme");
            request.setTargetPathPrefix("v1");

            assertApiException(() -> service.create(request), HttpStatus.BAD_REQUEST, "targetPathPrefix");
        }

        @ParameterizedTest
        @ValueSource(strings = {"ftp://files.example.com", "file:///etc/passwd", "not a url", "/relative/path",
                "service.example.com", "javascript:alert(1)"})
        void rejectsTargetBaseUrlThatIsNotHttpOrHttps(String url) {
            RouteRequest request = routeRequest("orders", "acme");
            request.setTargetBaseUrl(url);

            assertApiException(() -> service.create(request), HttpStatus.BAD_REQUEST, "targetBaseUrl");
        }

        @ParameterizedTest
        @ValueSource(strings = {"http://localhost:9000", "HTTPS://service.example.com", "https://a.b.c/base"})
        void acceptsHttpAndHttpsTargetBaseUrls(String url) {
            RouteRequest request = routeRequest("orders", "acme");
            request.setTargetBaseUrl(url);

            service.create(request);

            verify(apiRouteRepository).save(any(ApiRoute.class));
        }

        @ParameterizedTest
        @ValueSource(strings = {"SIDEWAYS", "both", ""})
        void rejectsUnknownHeaderDirection(String direction) {
            RouteRequest request = routeRequest("orders", "acme");
            request.getHeaderRules().set(0, headerRuleRequest(direction, "ADD", "X-A", "v"));

            assertApiException(() -> service.create(request), HttpStatus.BAD_REQUEST, "direction");
        }

        @ParameterizedTest
        @ValueSource(strings = {"REPLACE", "set", ""})
        void rejectsUnknownHeaderAction(String action) {
            RouteRequest request = routeRequest("orders", "acme");
            request.getHeaderRules().set(0, headerRuleRequest("REQUEST", action, "X-A", "v"));

            assertApiException(() -> service.create(request), HttpStatus.BAD_REQUEST, "action");
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", "   "})
        void rejectsAddRuleWithoutValue(String value) {
            RouteRequest request = routeRequest("orders", "acme");
            request.getHeaderRules().set(0, headerRuleRequest("REQUEST", "ADD", "X-A", value));

            assertApiException(() -> service.create(request), HttpStatus.BAD_REQUEST, "headerValue");
        }

        @Test
        void treatsDirectionAndActionCaseInsensitively() {
            RouteRequest request = routeRequest("orders", "acme");
            request.getHeaderRules().set(0, headerRuleRequest("response", "remove", "Server", null));

            service.create(request);

            ArgumentCaptor<ApiRoute> captor = ArgumentCaptor.forClass(ApiRoute.class);
            verify(apiRouteRepository).save(captor.capture());
            assertThat(captor.getValue().getHeaderRules().get(0).getDirection()).isEqualTo("RESPONSE");
            assertThat(captor.getValue().getHeaderRules().get(0).getAction()).isEqualTo("REMOVE");
        }

        @Test
        void propagatesTenantContextFailureBeforeTouchingAnyRepository() {
            when(tenantContext.getOrganization())
                    .thenThrow(new ApiException(HttpStatus.FORBIDDEN, "The organization is not active"));

            assertApiException(() -> service.create(routeRequest("orders", "acme")), HttpStatus.FORBIDDEN);

            verifyNoInteractions(apiRouteRepository, versionRepository, auditService);
        }
    }

    @Nested
    class Update {

        private ApiRoute existing;

        @BeforeEach
        void givenExistingRoute() {
            existing = apiRoute(10L, organization, "orders", true, 1L);
            existing.getHeaderRules().add(headerRule(existing, "REQUEST", "ADD", "X-Old", "old"));
            lenient().when(apiRouteRepository.findByIdAndOrganizationId(10L, ORG_ID))
                    .thenReturn(Optional.of(existing));
        }

        @Test
        void replacesFieldsMethodsAndRulesAndBumpsTheVersion() {
            RouteRequest request = routeRequest("orders", "acme");
            request.setName("Orders v2");
            request.setTargetBaseUrl("https://orders-v2.example.com");
            request.setRequestsPerMinute(null);
            request.setActive(false);
            request.setIdempotencyEnabled(true);

            RouteResponse response = service.update(10L, request);

            assertThat(existing.getName()).isEqualTo("Orders v2");
            assertThat(existing.getTargetBaseUrl()).isEqualTo("https://orders-v2.example.com");
            assertThat(existing.getRequestsPerMinute()).isNull();
            assertThat(existing.isActive()).isFalse();
            assertThat(existing.isIdempotencyEnabled()).isTrue();
            assertThat(existing.getConfigurationVersion()).isEqualTo(2L);
            assertThat(existing.getAllowedMethods()).containsExactlyInAnyOrder("GET", "POST");
            assertThat(existing.getHeaderRules()).hasSize(1);
            assertThat(existing.getHeaderRules().get(0).getHeaderName()).isEqualTo("X-Tenant");
            assertThat(response.getConfigurationVersion()).isEqualTo(2L);
        }

        @Test
        void writesUpdatedVersionAndAuditRecord() {
            service.update(10L, routeRequest("orders", "acme"));

            RouteConfigurationVersion version = capturedVersion();
            assertThat(version.getVersionNumber()).isEqualTo(2L);
            assertThat(version.getChangeType()).isEqualTo("UPDATED");
            assertThat(version.getRouteId()).isEqualTo("orders");
            verify(auditService).record(eq(organization), eq("UPDATE_ROUTE"), eq("API_ROUTE"), eq("orders"),
                    anyLong(), eq("Route configuration version 2 created"));
        }

        @Test
        void rejectsChangingTheRouteId() {
            assertApiException(() -> service.update(10L, routeRequest("renamed", "acme")),
                    HttpStatus.BAD_REQUEST, "routeId cannot be changed");

            assertThat(existing.getConfigurationVersion()).isEqualTo(1L);
            verifyNoInteractions(versionRepository, auditService);
        }

        @Test
        void rejectsRouteOfAnotherOrganizationAsNotFound() {
            when(apiRouteRepository.findByIdAndOrganizationId(99L, ORG_ID)).thenReturn(Optional.empty());

            assertApiException(() -> service.update(99L, routeRequest("orders", "acme")), HttpStatus.NOT_FOUND);

            verifyNoInteractions(versionRepository, auditService);
        }

        @Test
        void validatesTheNewConfigurationBeforeBumpingTheVersion() {
            RouteRequest request = routeRequest("orders", "acme");
            request.setPathPattern("/globex/orders");

            assertApiException(() -> service.update(10L, request), HttpStatus.BAD_REQUEST);

            assertThat(existing.getConfigurationVersion()).isEqualTo(1L);
            assertThat(existing.getHeaderRules()).hasSize(1);
            verifyNoInteractions(versionRepository, auditService);
        }
    }

    @Nested
    class ChangeStatus {

        private ApiRoute existing;

        @BeforeEach
        void givenExistingRoute() {
            existing = apiRoute(10L, organization, "orders", false, 1L);
            when(apiRouteRepository.findByIdAndOrganizationId(10L, ORG_ID)).thenReturn(Optional.of(existing));
        }

        @Test
        void activatesTheRoute() {
            RouteStatusRequest request = new RouteStatusRequest();
            request.setActive(true);

            RouteResponse response = service.changeStatus(10L, request);

            assertThat(existing.isActive()).isTrue();
            assertThat(existing.getConfigurationVersion()).isEqualTo(2L);
            assertThat(response.isActive()).isTrue();
            assertThat(capturedVersion().getChangeType()).isEqualTo("ACTIVATED");
            verify(auditService).record(eq(organization), eq("ACTIVATE_ROUTE"), eq("API_ROUTE"), eq("orders"),
                    anyLong(), eq("Route configuration version 2 created"));
        }

        @Test
        void deactivatesTheRoute() {
            existing.setActive(true);
            RouteStatusRequest request = new RouteStatusRequest();
            request.setActive(false);

            service.changeStatus(10L, request);

            assertThat(existing.isActive()).isFalse();
            assertThat(existing.getConfigurationVersion()).isEqualTo(2L);
            assertThat(capturedVersion().getChangeType()).isEqualTo("DEACTIVATED");
            verify(auditService).record(eq(organization), eq("DEACTIVATE_ROUTE"), eq("API_ROUTE"), eq("orders"),
                    anyLong(), eq("Route configuration version 2 created"));
        }

        @Test
        void snapshotReflectsTheNewState() {
            RouteStatusRequest request = new RouteStatusRequest();
            request.setActive(true);

            service.changeStatus(10L, request);

            assertThat(capturedVersion().getConfigurationSnapshot()).contains("\"active\":true");
        }
    }

    @Nested
    class Delete {

        @Test
        void savesFinalDeletedVersionThenRemovesTheRoute() {
            ApiRoute existing = apiRoute(10L, organization, "orders", true, 3L);
            when(apiRouteRepository.findByIdAndOrganizationId(10L, ORG_ID)).thenReturn(Optional.of(existing));

            service.delete(10L);

            InOrder order = inOrder(versionRepository, auditService, apiRouteRepository);
            order.verify(versionRepository).save(any(RouteConfigurationVersion.class));
            order.verify(auditService).record(eq(organization), eq("DELETE_ROUTE"), eq("API_ROUTE"), eq("orders"),
                    anyLong(), eq("Final configuration saved as version 4"));
            order.verify(apiRouteRepository).delete(existing);

            ArgumentCaptor<RouteConfigurationVersion> captor = ArgumentCaptor.forClass(RouteConfigurationVersion.class);
            verify(versionRepository).save(captor.capture());
            assertThat(captor.getValue().getVersionNumber()).isEqualTo(4L);
            assertThat(captor.getValue().getChangeType()).isEqualTo("DELETED");
        }

        @Test
        void reportsUnknownRouteAsNotFoundWithoutDeleting() {
            when(apiRouteRepository.findByIdAndOrganizationId(99L, ORG_ID)).thenReturn(Optional.empty());

            assertApiException(() -> service.delete(99L), HttpStatus.NOT_FOUND);

            verify(apiRouteRepository, never()).delete(any());
            verifyNoInteractions(versionRepository, auditService);
        }
    }

    @Nested
    class Queries {

        @Test
        void findAllForTenantScopesToTheOrganization() {
            ApiRoute first = apiRoute(1L, organization, "a", true, 1L);
            ApiRoute second = apiRoute(2L, organization, "b", false, 2L);
            when(apiRouteRepository.findAllByOrganizationId(ORG_ID)).thenReturn(List.of(first, second));

            List<RouteResponse> result = service.findAllForTenant();

            assertThat(result).extracting(RouteResponse::getRouteId).containsExactly("a", "b");
            assertThat(result).extracting(RouteResponse::getOrganizationId).containsOnly(ORG_ID);
        }

        @Test
        void findAllForTenantReturnsEmptyListWhenNoRoutes() {
            when(apiRouteRepository.findAllByOrganizationId(ORG_ID)).thenReturn(List.of());

            assertThat(service.findAllForTenant()).isEmpty();
        }

        @Test
        void findByIdReturnsOwnedRoute() {
            when(apiRouteRepository.findByIdAndOrganizationId(10L, ORG_ID))
                    .thenReturn(Optional.of(apiRoute(10L, organization, "orders", true, 1L)));

            RouteResponse response = service.findById(10L);

            assertThat(response.getId()).isEqualTo(10L);
            assertThat(response.getRouteId()).isEqualTo("orders");
        }

        @Test
        void findByIdReportsForeignOrMissingRouteAsNotFound() {
            when(apiRouteRepository.findByIdAndOrganizationId(10L, ORG_ID)).thenReturn(Optional.empty());

            assertApiException(() -> service.findById(10L), HttpStatus.NOT_FOUND, "Route not found");
        }

        @Test
        void getVersionsKeepsRepositoryOrderAndScopesByOrganizationAndRouteId() {
            LocalDateTime now = LocalDateTime.now();
            RouteConfigurationVersion newer = version(2L, "orders", 2L, "UPDATED", now);
            RouteConfigurationVersion older = version(1L, "orders", 1L, "CREATED", now.minusDays(1));
            when(versionRepository.findAllByOrganizationIdAndRouteIdOrderByVersionNumberDesc(ORG_ID, "orders"))
                    .thenReturn(List.of(newer, older));

            List<RouteVersionResponse> result = service.getVersions("orders");

            assertThat(result).extracting(RouteVersionResponse::getVersionNumber).containsExactly(2L, 1L);
            assertThat(result).extracting(RouteVersionResponse::getChangeType).containsExactly("UPDATED", "CREATED");
        }

        @Test
        void findAllActiveForGatewayAsksForActiveRoutesOfActiveOrganizations() {
            Organization other = activeOrganization(8L, "globex");
            when(apiRouteRepository.findAllByActiveTrueAndOrganizationStatus(OrganizationStatus.ACTIVE))
                    .thenReturn(List.of(apiRoute(1L, organization, "a", true, 1L),
                            apiRoute(2L, other, "b", true, 1L)));

            List<RouteResponse> result = service.findAllActiveForGateway();

            assertThat(result).extracting(RouteResponse::getOrganizationId).containsExactly(ORG_ID, 8L);
            verifyNoInteractions(tenantContext);
        }

        @Test
        void getAuditRecordsMapsRecordsForTheOrganization() {
            ControlPlaneAuditRecord record = new ControlPlaneAuditRecord();
            record.setId(5L);
            record.setActor("acme-admin");
            record.setAction("CREATE_ROUTE");
            record.setResourceType("API_ROUTE");
            record.setResourceId("orders");
            record.setCorrelationId("corr-1");
            record.setDurationMs(12L);
            record.setResult("SUCCESS");
            record.setDetails("Route configuration version 1 created");
            record.setCreatedAt(LocalDateTime.of(2026, 10, 29, 12, 0));
            when(auditRecordRepository.findAllByOrganizationIdOrderByCreatedAtDesc(ORG_ID))
                    .thenReturn(List.of(record));

            List<AuditRecordResponse> result = service.getAuditRecords();

            assertThat(result).hasSize(1);
            AuditRecordResponse response = result.get(0);
            assertThat(response.getId()).isEqualTo(5L);
            assertThat(response.getActor()).isEqualTo("acme-admin");
            assertThat(response.getAction()).isEqualTo("CREATE_ROUTE");
            assertThat(response.getResourceId()).isEqualTo("orders");
            assertThat(response.getCorrelationId()).isEqualTo("corr-1");
            assertThat(response.getDurationMs()).isEqualTo(12L);
            assertThat(response.getResult()).isEqualTo("SUCCESS");
        }

        @Test
        void tenantQueriesFailWhenTheTenantContextRejectsTheCaller() {
            when(tenantContext.getOrganization())
                    .thenThrow(new ApiException(HttpStatus.FORBIDDEN, "Tenant identity is missing"));

            assertThatThrownBy(() -> service.findAllForTenant()).isInstanceOf(ApiException.class);
            assertThatThrownBy(() -> service.getVersions("orders")).isInstanceOf(ApiException.class);
            assertThatThrownBy(() -> service.getAuditRecords()).isInstanceOf(ApiException.class);
            verifyNoInteractions(apiRouteRepository, versionRepository, auditRecordRepository);
        }

        private RouteConfigurationVersion version(Long id, String routeId, Long number, String type,
                                                  LocalDateTime createdAt) {
            RouteConfigurationVersion version = new RouteConfigurationVersion();
            version.setId(id);
            version.setOrganization(organization);
            version.setRouteId(routeId);
            version.setVersionNumber(number);
            version.setChangeType(type);
            version.setConfigurationSnapshot("{}");
            version.setCreatedAt(createdAt);
            return version;
        }
    }

    @Test
    void routeResponseMapsAllowedMethodsAndRules() {
        ApiRoute route = apiRoute(10L, organization, "orders", true, 1L);
        route.setAllowedMethods(Set.of("GET", "DELETE"));
        route.getHeaderRules().add(headerRule(route, "REQUEST", "ADD", "X-A", "1"));

        RouteResponse response = RouteResponse.from(route);

        assertThat(response.getAllowedMethods()).containsExactlyInAnyOrder("GET", "DELETE");
        assertThat(response.getHeaderRules()).hasSize(1);
        assertThat(response.getHeaderRules().get(0).getHeaderName()).isEqualTo("X-A");
    }
}
