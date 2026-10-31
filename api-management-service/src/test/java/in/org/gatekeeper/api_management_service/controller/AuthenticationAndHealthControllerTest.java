package in.org.gatekeeper.api_management_service.controller;

import in.org.gatekeeper.api_management_service.config.SecurityConfig;
import in.org.gatekeeper.api_management_service.dto.request.LoginRequest;
import in.org.gatekeeper.api_management_service.dto.response.LoginResponse;
import in.org.gatekeeper.api_management_service.service.AuthenticationService;
import in.org.gatekeeper.api_management_service.service.HealthService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({AuthenticationController.class, HealthController.class})
@Import(SecurityConfig.class)
class AuthenticationAndHealthControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuthenticationService authenticationService;

    @MockitoBean
    private HealthService healthService;

    @Nested
    class Login {

        @Test
        void isPublicAndReturnsTheTokenResponse() throws Exception {
            when(authenticationService.login(any(LoginRequest.class)))
                    .thenReturn(new LoginResponse("jwt-value", "Bearer", 3600, "TENANT_ADMIN", 7L));

            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"acme-admin\",\"password\":\"secret\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").value("jwt-value"))
                    .andExpect(jsonPath("$.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.expiresInSeconds").value(3600))
                    .andExpect(jsonPath("$.role").value("TENANT_ADMIN"))
                    .andExpect(jsonPath("$.organizationId").value(7));

            ArgumentCaptor<LoginRequest> captor = ArgumentCaptor.forClass(LoginRequest.class);
            verify(authenticationService).login(captor.capture());
            assertThat(captor.getValue().getUsername()).isEqualTo("acme-admin");
            assertThat(captor.getValue().getPassword()).isEqualTo("secret");
        }

        @Test
        void platformLoginHasNullOrganizationId() throws Exception {
            when(authenticationService.login(any(LoginRequest.class)))
                    .thenReturn(new LoginResponse("jwt-value", "Bearer", 3600, "PLATFORM_ADMIN", null));

            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"platform-admin\",\"password\":\"secret\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.organizationId").value((Object) null));
        }

        @ParameterizedTest
        @CsvSource(delimiter = '|', textBlock = """
                {}
                {"username":"u"}
                {"password":"p"}
                {"username":"","password":"p"}
                {"username":"u","password":"   "}
                """)
        void rejectsIncompleteCredentialsWith400(String body) throws Exception {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(authenticationService);
        }

        @Test
        void rejectsMalformedJsonWith400() throws Exception {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("not json"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void onlyAcceptsPost() throws Exception {
            mvc.perform(get("/api/auth/login")).andExpect(status().isMethodNotAllowed());
        }
    }

    @Nested
    class Health {

        @Test
        void isPublicAndReportsDatabaseUp() throws Exception {
            when(healthService.getHealth()).thenReturn("UP");

            mvc.perform(get("/health"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.serviceStatus").value("UP"))
                    .andExpect(jsonPath("$.dbStatus").value("UP"))
                    .andExpect(jsonPath("$.timestamp").exists());
        }

        @Test
        void keepsServiceUpWhenDatabaseIsDown() throws Exception {
            when(healthService.getHealth()).thenReturn("DOWN");

            mvc.perform(get("/health"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.serviceStatus").value("UP"))
                    .andExpect(jsonPath("$.dbStatus").value("DOWN"));
        }
    }
}
