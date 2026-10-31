package in.org.gatekeeper.api_management_service.controller;

import in.org.gatekeeper.api_management_service.config.SecurityConfig;
import in.org.gatekeeper.api_management_service.dto.request.RouteRequest;
import in.org.gatekeeper.api_management_service.dto.request.RouteStatusRequest;
import in.org.gatekeeper.api_management_service.dto.response.AuditRecordResponse;
import in.org.gatekeeper.api_management_service.dto.response.RouteResponse;
import in.org.gatekeeper.api_management_service.dto.response.RouteVersionResponse;
import in.org.gatekeeper.api_management_service.entity.ApiRoute;
import in.org.gatekeeper.api_management_service.entity.ControlPlaneAuditRecord;
import in.org.gatekeeper.api_management_service.entity.RouteConfigurationVersion;
import in.org.gatekeeper.api_management_service.service.RouteService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static in.org.gatekeeper.api_management_service.support.Roles.gatewayService;
import static in.org.gatekeeper.api_management_service.support.Roles.platformAdmin;
import static in.org.gatekeeper.api_management_service.support.Roles.tenantAdmin;
import static in.org.gatekeeper.api_management_service.support.TestData.activeOrganization;
import static in.org.gatekeeper.api_management_service.support.TestData.apiRoute;
import static in.org.gatekeeper.api_management_service.support.TestData.headerRule;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TenantRouteController.class)
@Import(SecurityConfig.class)
class TenantRouteControllerTest {

    private static final String VALID_ROUTE = """
            {
              "routeId": "orders",
              "name": "Orders API",
              "pathPattern": "/acme/orders",
              "targetBaseUrl": "https://orders.internal",
              "targetPathPrefix": "/v1",
              "requestsPerMinute": 600,
              "responseTimeoutMs": 5000,
              "idempotencyEnabled": true,
              "active": true,
              "allowedMethods": ["GET", "POST"],
              "headerRules": [
                {"direction": "REQUEST", "action": "ADD", "headerName": "X-Tenant", "headerValue": "acme"}
              ]
            }
            """;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private RouteService routeService;

    private RouteResponse sampleResponse() {
        ApiRoute route = apiRoute(10L, activeOrganization(7L, "acme"), "orders", true, 2L);
        route.getHeaderRules().add(headerRule(route, "REQUEST", "ADD", "X-Tenant", "acme"));
        return RouteResponse.from(route);
    }

    @Nested
    class Create {

        @Test
        void returns201WithTheCreatedRouteAndPassesTheParsedBodyToTheService() throws Exception {
            when(routeService.create(any(RouteRequest.class))).thenReturn(sampleResponse());

            mvc.perform(post("/api/tenant/routes").with(tenantAdmin())
                            .contentType(MediaType.APPLICATION_JSON).content(VALID_ROUTE))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(10))
                    .andExpect(jsonPath("$.routeId").value("orders"))
                    .andExpect(jsonPath("$.organizationId").value(7))
                    .andExpect(jsonPath("$.configurationVersion").value(2))
                    .andExpect(jsonPath("$.allowedMethods").isArray())
                    .andExpect(jsonPath("$.headerRules[0].headerName").value("X-Tenant"));

            ArgumentCaptor<RouteRequest> captor = ArgumentCaptor.forClass(RouteRequest.class);
            verify(routeService).create(captor.capture());
            RouteRequest sent = captor.getValue();
            assertThat(sent.getRouteId()).isEqualTo("orders");
            assertThat(sent.getRequestsPerMinute()).isEqualTo(600);
            assertThat(sent.getResponseTimeoutMs()).isEqualTo(5000);
            assertThat(sent.isIdempotencyEnabled()).isTrue();
            assertThat(sent.isActive()).isTrue();
            assertThat(sent.getAllowedMethods()).containsExactlyInAnyOrder("GET", "POST");
            assertThat(sent.getHeaderRules()).hasSize(1);
            assertThat(sent.getHeaderRules().get(0).getHeaderValue()).isEqualTo("acme");
        }

        @Test
        void acceptsRequestWithoutRateLimit() throws Exception {
            when(routeService.create(any(RouteRequest.class))).thenReturn(sampleResponse());
            String body = VALID_ROUTE.replace("\"requestsPerMinute\": 600,", "\"requestsPerMinute\": null,");

            mvc.perform(post("/api/tenant/routes").with(tenantAdmin())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isCreated());
        }

        @Test
        void rejectsEmptyBodyObjectWith400() throws Exception {
            mvc.perform(post("/api/tenant/routes").with(tenantAdmin())
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(routeService);
        }

        @Test
        void rejectsMalformedJsonWith400() throws Exception {
            mvc.perform(post("/api/tenant/routes").with(tenantAdmin())
                            .contentType(MediaType.APPLICATION_JSON).content("{not json"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(routeService);
        }

        @Test
        void rejectsMissingBodyWith400() throws Exception {
            mvc.perform(post("/api/tenant/routes").with(tenantAdmin()).contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void rejectsEmptyMethodListWith400() throws Exception {
            String body = VALID_ROUTE.replace("[\"GET\", \"POST\"]", "[]");

            mvc.perform(post("/api/tenant/routes").with(tenantAdmin())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(routeService);
        }

        @Test
        void rejectsZeroTimeoutWith400() throws Exception {
            String body = VALID_ROUTE.replace("\"responseTimeoutMs\": 5000", "\"responseTimeoutMs\": 0");

            mvc.perform(post("/api/tenant/routes").with(tenantAdmin())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(routeService);
        }

        @Test
        void rejectsInvalidNestedHeaderRuleWith400() throws Exception {
            String body = VALID_ROUTE.replace("\"headerName\": \"X-Tenant\"", "\"headerName\": \"\"");

            mvc.perform(post("/api/tenant/routes").with(tenantAdmin())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(routeService);
        }

        @Test
        void rejectsNonJsonContentTypeWith415() throws Exception {
            mvc.perform(post("/api/tenant/routes").with(tenantAdmin())
                            .contentType(MediaType.TEXT_PLAIN).content(VALID_ROUTE))
                    .andExpect(status().isUnsupportedMediaType());

            verifyNoInteractions(routeService);
        }
    }

    @Nested
    class Read {

        @Test
        void listsRoutes() throws Exception {
            when(routeService.findAllForTenant()).thenReturn(List.of(sampleResponse()));

            mvc.perform(get("/api/tenant/routes").with(tenantAdmin()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].routeId").value("orders"));
        }

        @Test
        void listsNothingAsEmptyArray() throws Exception {
            when(routeService.findAllForTenant()).thenReturn(List.of());

            mvc.perform(get("/api/tenant/routes").with(tenantAdmin()))
                    .andExpect(status().isOk())
                    .andExpect(content().json("[]"));
        }

        @Test
        void getsRouteById() throws Exception {
            when(routeService.findById(10L)).thenReturn(sampleResponse());

            mvc.perform(get("/api/tenant/routes/10").with(tenantAdmin()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(10))
                    .andExpect(jsonPath("$.pathPattern").value("/acme/orders"))
                    .andExpect(jsonPath("$.active").value(true));
        }

        @Test
        void rejectsNonNumericIdWith400() throws Exception {
            mvc.perform(get("/api/tenant/routes/abc").with(tenantAdmin()))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(routeService);
        }

        @Test
        void listsVersionsByBusinessRouteId() throws Exception {
            RouteConfigurationVersion version = new RouteConfigurationVersion();
            version.setId(40L);
            version.setRouteId("orders");
            version.setVersionNumber(3L);
            version.setChangeType("UPDATED");
            version.setConfigurationSnapshot("{\"routeId\":\"orders\"}");
            version.setCreatedAt(LocalDateTime.of(2026, 10, 29, 10, 0));
            when(routeService.getVersions("orders")).thenReturn(List.of(RouteVersionResponse.from(version)));

            mvc.perform(get("/api/tenant/route-versions/orders").with(tenantAdmin()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].versionNumber").value(3))
                    .andExpect(jsonPath("$[0].changeType").value("UPDATED"))
                    .andExpect(jsonPath("$[0].configurationSnapshot").value("{\"routeId\":\"orders\"}"));
        }

        @Test
        void listsAuditRecords() throws Exception {
            ControlPlaneAuditRecord record = new ControlPlaneAuditRecord();
            record.setId(90L);
            record.setActor("acme-admin");
            record.setAction("CREATE_ROUTE");
            record.setResourceType("API_ROUTE");
            record.setResourceId("orders");
            record.setResult("SUCCESS");
            record.setDurationMs(5L);
            record.setCreatedAt(LocalDateTime.of(2026, 10, 29, 10, 0));
            when(routeService.getAuditRecords()).thenReturn(List.of(AuditRecordResponse.from(record)));

            mvc.perform(get("/api/tenant/audit-records").with(tenantAdmin()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].actor").value("acme-admin"))
                    .andExpect(jsonPath("$[0].action").value("CREATE_ROUTE"))
                    .andExpect(jsonPath("$[0].result").value("SUCCESS"));
        }
    }

    @Nested
    class Update {

        @Test
        void putsRouteAndReturnsTheUpdatedRepresentation() throws Exception {
            when(routeService.update(eq(10L), any(RouteRequest.class))).thenReturn(sampleResponse());

            mvc.perform(put("/api/tenant/routes/10").with(tenantAdmin())
                            .contentType(MediaType.APPLICATION_JSON).content(VALID_ROUTE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.routeId").value("orders"));

            ArgumentCaptor<RouteRequest> captor = ArgumentCaptor.forClass(RouteRequest.class);
            verify(routeService).update(eq(10L), captor.capture());
            assertThat(captor.getValue().getName()).isEqualTo("Orders API");
        }

        @Test
        void rejectsInvalidBodyWith400() throws Exception {
            mvc.perform(put("/api/tenant/routes/10").with(tenantAdmin())
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(routeService);
        }
    }

    @Nested
    class ChangeStatus {

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void patchesActiveFlag(boolean active) throws Exception {
            when(routeService.changeStatus(eq(10L), any(RouteStatusRequest.class))).thenReturn(sampleResponse());

            mvc.perform(patch("/api/tenant/routes/10/status").with(tenantAdmin())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"active\":" + active + "}"))
                    .andExpect(status().isOk());

            ArgumentCaptor<RouteStatusRequest> captor = ArgumentCaptor.forClass(RouteStatusRequest.class);
            verify(routeService).changeStatus(eq(10L), captor.capture());
            assertThat(captor.getValue().getActive()).isEqualTo(active);
        }

        @Test
        void requiresTheActiveFlag() throws Exception {
            mvc.perform(patch("/api/tenant/routes/10/status").with(tenantAdmin())
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(routeService);
        }

        @Test
        void rejectsNonBooleanValue() throws Exception {
            mvc.perform(patch("/api/tenant/routes/10/status").with(tenantAdmin())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"active\":\"maybe\"}"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(routeService);
        }
    }

    @Nested
    class Delete {

        @Test
        void returns204WithoutBody() throws Exception {
            mvc.perform(delete("/api/tenant/routes/10").with(tenantAdmin()))
                    .andExpect(status().isNoContent())
                    .andExpect(content().string(""));

            verify(routeService).delete(10L);
        }

        @Test
        void rejectsNonNumericIdWith400() throws Exception {
            mvc.perform(delete("/api/tenant/routes/abc").with(tenantAdmin()))
                    .andExpect(status().isBadRequest());

            verify(routeService, never()).delete(any());
        }
    }

    @Nested
    class Authorization {

        private static final String ENDPOINTS = """
                GET,/api/tenant/routes
                POST,/api/tenant/routes
                GET,/api/tenant/routes/10
                PUT,/api/tenant/routes/10
                PATCH,/api/tenant/routes/10/status
                DELETE,/api/tenant/routes/10
                GET,/api/tenant/route-versions/orders
                GET,/api/tenant/audit-records
                """;

        @ParameterizedTest
        @CsvSource(textBlock = ENDPOINTS)
        void rejectsAnonymousCallersWith401(String method, String path) throws Exception {
            mvc.perform(request(HttpMethod.valueOf(method), path))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(routeService);
        }

        @ParameterizedTest
        @CsvSource(textBlock = ENDPOINTS)
        void rejectsPlatformAdminWith403(String method, String path) throws Exception {
            mvc.perform(request(HttpMethod.valueOf(method), path).with(platformAdmin()))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(routeService);
        }

        @ParameterizedTest
        @CsvSource(textBlock = ENDPOINTS)
        void rejectsGatewayServiceWith403(String method, String path) throws Exception {
            mvc.perform(request(HttpMethod.valueOf(method), path).with(gatewayService()))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(routeService);
        }
    }
}
