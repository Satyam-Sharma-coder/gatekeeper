package in.org.gatekeeper.api_management_service.controller;

import in.org.gatekeeper.api_management_service.config.SecurityConfig;
import in.org.gatekeeper.api_management_service.dto.request.TenantOrganizationRequest;
import in.org.gatekeeper.api_management_service.dto.response.OrganizationResponse;
import in.org.gatekeeper.api_management_service.entity.OrganizationStatus;
import in.org.gatekeeper.api_management_service.service.OrganizationService;
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

import static in.org.gatekeeper.api_management_service.support.Roles.gatewayService;
import static in.org.gatekeeper.api_management_service.support.Roles.platformAdmin;
import static in.org.gatekeeper.api_management_service.support.Roles.tenantAdmin;
import static in.org.gatekeeper.api_management_service.support.TestData.organization;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TenantOrganizationController.class)
@Import(SecurityConfig.class)
class TenantOrganizationControllerTest {

    private static final String PATH = "/api/tenant/organization";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private OrganizationService organizationService;

    private OrganizationResponse own() {
        return OrganizationResponse.from(organization(7L, "acme", OrganizationStatus.ACTIVE));
    }

    @Test
    void getReturnsTheCallersOrganization() throws Exception {
        when(organizationService.getOwnOrganization()).thenReturn(own());

        mvc.perform(get(PATH).with(tenantAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.slug").value("acme"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void putRenamesTheOrganization() throws Exception {
        when(organizationService.updateOwnOrganization(any(TenantOrganizationRequest.class))).thenReturn(own());

        mvc.perform(put(PATH).with(tenantAdmin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Acme International\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<TenantOrganizationRequest> captor = ArgumentCaptor.forClass(TenantOrganizationRequest.class);
        verify(organizationService).updateOwnOrganization(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Acme International");
    }

    @Test
    void putIgnoresAttemptsToChangeSlugOrStatus() throws Exception {
        when(organizationService.updateOwnOrganization(any(TenantOrganizationRequest.class))).thenReturn(own());

        mvc.perform(put(PATH).with(tenantAdmin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Acme\",\"slug\":\"hijack\",\"status\":\"DELETED\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<TenantOrganizationRequest> captor = ArgumentCaptor.forClass(TenantOrganizationRequest.class);
        verify(organizationService).updateOwnOrganization(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Acme");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            {}
            {"name":""}
            {"name":"   "}
            """)
    void putRejectsMissingOrBlankName(String body) throws Exception {
        mvc.perform(put(PATH).with(tenantAdmin()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(organizationService);
    }

    @ParameterizedTest
    @CsvSource({"GET", "PUT"})
    void rejectsAnonymousCallersWith401(String method) throws Exception {
        mvc.perform(request(HttpMethod.valueOf(method), PATH)).andExpect(status().isUnauthorized());

        verifyNoInteractions(organizationService);
    }

    @ParameterizedTest
    @CsvSource({"GET", "PUT"})
    void rejectsPlatformAdminAndGatewayWith403(String method) throws Exception {
        mvc.perform(request(HttpMethod.valueOf(method), PATH).with(platformAdmin()))
                .andExpect(status().isForbidden());
        mvc.perform(request(HttpMethod.valueOf(method), PATH).with(gatewayService()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(organizationService);
    }
}
