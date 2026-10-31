package in.org.gatekeeper.api_management_service.controller;

import in.org.gatekeeper.api_management_service.config.SecurityConfig;
import in.org.gatekeeper.api_management_service.dto.request.OrganizationRequest;
import in.org.gatekeeper.api_management_service.dto.request.OrganizationStatusRequest;
import in.org.gatekeeper.api_management_service.dto.request.TenantAdminRequest;
import in.org.gatekeeper.api_management_service.dto.response.OrganizationResponse;
import in.org.gatekeeper.api_management_service.dto.response.TenantAdminResponse;
import in.org.gatekeeper.api_management_service.entity.ManagementUser;
import in.org.gatekeeper.api_management_service.entity.OrganizationStatus;
import in.org.gatekeeper.api_management_service.entity.Role;
import in.org.gatekeeper.api_management_service.service.OrganizationService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static in.org.gatekeeper.api_management_service.support.Roles.gatewayService;
import static in.org.gatekeeper.api_management_service.support.Roles.platformAdmin;
import static in.org.gatekeeper.api_management_service.support.Roles.tenantAdmin;
import static in.org.gatekeeper.api_management_service.support.TestData.activeOrganization;
import static in.org.gatekeeper.api_management_service.support.TestData.organization;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

@WebMvcTest(PlatformOrganizationController.class)
@Import(SecurityConfig.class)
class PlatformOrganizationControllerTest {

    private static final String BASE = "/api/platform/organizations";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private OrganizationService organizationService;

    private OrganizationResponse acme() {
        return OrganizationResponse.from(organization(7L, "acme", OrganizationStatus.PENDING));
    }

    private TenantAdminResponse adminResponse() {
        Role role = new Role();
        role.setName("TENANT_ADMIN");
        return TenantAdminResponse.from(ManagementUser.builder().id(3L).organization(activeOrganization(7L, "acme"))
                .role(role).username("acme-admin").enabled(true).passwordHash("hash").build());
    }

    @Nested
    class Organizations {

        @Test
        void createReturns201() throws Exception {
            when(organizationService.create(any(OrganizationRequest.class))).thenReturn(acme());

            mvc.perform(post(BASE).with(platformAdmin()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Acme Corp\",\"slug\":\"acme-corp\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(7))
                    .andExpect(jsonPath("$.slug").value("acme"))
                    .andExpect(jsonPath("$.status").value("PENDING"));

            ArgumentCaptor<OrganizationRequest> captor = ArgumentCaptor.forClass(OrganizationRequest.class);
            verify(organizationService).create(captor.capture());
            assertThat(captor.getValue().getName()).isEqualTo("Acme Corp");
            assertThat(captor.getValue().getSlug()).isEqualTo("acme-corp");
        }

        @ParameterizedTest
        @CsvSource(delimiter = '|', textBlock = """
                {}
                {"name":"Acme"}
                {"slug":"acme"}
                {"name":"Acme","slug":"Not Valid"}
                {"name":"Acme","slug":"-leading"}
                {"name":"","slug":"acme"}
                """)
        void createRejectsInvalidBodyWith400(String body) throws Exception {
            mvc.perform(post(BASE).with(platformAdmin()).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(organizationService);
        }

        @Test
        void listReturnsOrganizations() throws Exception {
            when(organizationService.findAll()).thenReturn(List.of(acme()));

            mvc.perform(get(BASE).with(platformAdmin()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].name").value("Org acme"));
        }

        @Test
        void getReturnsOrganization() throws Exception {
            when(organizationService.findById(7L)).thenReturn(acme());

            mvc.perform(get(BASE + "/7").with(platformAdmin()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(7));
        }

        @Test
        void getRejectsNonNumericId() throws Exception {
            mvc.perform(get(BASE + "/seven").with(platformAdmin())).andExpect(status().isBadRequest());
        }

        @Test
        void updateReturnsUpdatedOrganization() throws Exception {
            when(organizationService.update(eq(7L), any(OrganizationRequest.class))).thenReturn(acme());

            mvc.perform(put(BASE + "/7").with(platformAdmin()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Acme\",\"slug\":\"acme\"}"))
                    .andExpect(status().isOk());
        }

        @Test
        void updateRejectsInvalidSlugWith400() throws Exception {
            mvc.perform(put(BASE + "/7").with(platformAdmin()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Acme\",\"slug\":\"UPPER\"}"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(organizationService);
        }

        @Test
        void patchStatusPassesTheEnumToTheService() throws Exception {
            when(organizationService.changeStatus(eq(7L), any(OrganizationStatusRequest.class))).thenReturn(acme());

            mvc.perform(patch(BASE + "/7/status").with(platformAdmin()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"SUSPENDED\"}"))
                    .andExpect(status().isOk());

            ArgumentCaptor<OrganizationStatusRequest> captor = ArgumentCaptor.forClass(OrganizationStatusRequest.class);
            verify(organizationService).changeStatus(eq(7L), captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(OrganizationStatus.SUSPENDED);
        }

        @ParameterizedTest
        @CsvSource(delimiter = '|', textBlock = """
                {}
                {"status":"BOGUS"}
                {"status":null}
                """)
        void patchStatusRejectsMissingOrUnknownStatus(String body) throws Exception {
            mvc.perform(patch(BASE + "/7/status").with(platformAdmin()).contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(organizationService);
        }

        @Test
        void deleteReturns204() throws Exception {
            mvc.perform(delete(BASE + "/7").with(platformAdmin()))
                    .andExpect(status().isNoContent())
                    .andExpect(content().string(""));

            verify(organizationService).delete(7L);
        }
    }

    @Nested
    class TenantAdmins {

        private static final String VALID = "{\"username\":\"acme-admin\",\"password\":\"secret-123\",\"enabled\":true}";

        @Test
        void createReturns201AndNeverEchoesThePassword() throws Exception {
            when(organizationService.createTenantAdmin(eq(7L), any(TenantAdminRequest.class)))
                    .thenReturn(adminResponse());

            mvc.perform(post(BASE + "/7/tenant-admin").with(platformAdmin())
                            .contentType(MediaType.APPLICATION_JSON).content(VALID))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.username").value("acme-admin"))
                    .andExpect(jsonPath("$.organizationId").value(7))
                    .andExpect(jsonPath("$.enabled").value(true))
                    .andExpect(jsonPath("$.password").doesNotExist())
                    .andExpect(jsonPath("$.passwordHash").doesNotExist());

            ArgumentCaptor<TenantAdminRequest> captor = ArgumentCaptor.forClass(TenantAdminRequest.class);
            verify(organizationService).createTenantAdmin(eq(7L), captor.capture());
            assertThat(captor.getValue().getPassword()).isEqualTo("secret-123");
        }

        @ParameterizedTest
        @CsvSource(delimiter = '|', textBlock = """
                {}
                {"username":"u"}
                {"password":"secret-123"}
                {"username":"u","password":"short"}
                {"username":"","password":"secret-123"}
                """)
        void createRejectsInvalidBodyWith400(String body) throws Exception {
            mvc.perform(post(BASE + "/7/tenant-admin").with(platformAdmin())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(organizationService);
        }

        @Test
        void getReturnsAdmin() throws Exception {
            when(organizationService.getTenantAdmin(7L)).thenReturn(adminResponse());

            mvc.perform(get(BASE + "/7/tenant-admin").with(platformAdmin()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(3));
        }

        @Test
        void updateReturnsAdmin() throws Exception {
            when(organizationService.updateTenantAdmin(eq(7L), any(TenantAdminRequest.class)))
                    .thenReturn(adminResponse());

            mvc.perform(put(BASE + "/7/tenant-admin").with(platformAdmin())
                            .contentType(MediaType.APPLICATION_JSON).content(VALID))
                    .andExpect(status().isOk());
        }

        @Test
        void updateRequiresAPassword() throws Exception {
            mvc.perform(put(BASE + "/7/tenant-admin").with(platformAdmin())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"acme-admin\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void deleteReturns204() throws Exception {
            mvc.perform(delete(BASE + "/7/tenant-admin").with(platformAdmin()))
                    .andExpect(status().isNoContent());

            verify(organizationService).deleteTenantAdmin(7L);
        }
    }

    @Nested
    class Authorization {

        private static final String ENDPOINTS = """
                POST,/api/platform/organizations
                GET,/api/platform/organizations
                GET,/api/platform/organizations/7
                PUT,/api/platform/organizations/7
                PATCH,/api/platform/organizations/7/status
                DELETE,/api/platform/organizations/7
                POST,/api/platform/organizations/7/tenant-admin
                GET,/api/platform/organizations/7/tenant-admin
                PUT,/api/platform/organizations/7/tenant-admin
                DELETE,/api/platform/organizations/7/tenant-admin
                """;

        @ParameterizedTest
        @CsvSource(textBlock = ENDPOINTS)
        void rejectsAnonymousCallersWith401(String method, String path) throws Exception {
            mvc.perform(request(HttpMethod.valueOf(method), path)).andExpect(status().isUnauthorized());

            verifyNoInteractions(organizationService);
        }

        @ParameterizedTest
        @CsvSource(textBlock = ENDPOINTS)
        void rejectsTenantAdminWith403(String method, String path) throws Exception {
            mvc.perform(request(HttpMethod.valueOf(method), path).with(tenantAdmin()))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(organizationService);
        }

        @ParameterizedTest
        @CsvSource(textBlock = ENDPOINTS)
        void rejectsGatewayServiceWith403(String method, String path) throws Exception {
            mvc.perform(request(HttpMethod.valueOf(method), path).with(gatewayService()))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(organizationService);
        }
    }
}
