package in.org.gatekeeper.analytics_service.controller;

import in.org.gatekeeper.analytics_service.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HealthController.class)
@Import(SecurityConfig.class)
class HealthControllerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void healthIsPublicAndReportsUpWithTheConfiguredServiceInfo() throws Exception {
        mvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appName").value("Analytics Service"))
                .andExpect(jsonPath("$.appDescription").value("Custom API Gateway for routing, security and processing"))
                .andExpect(jsonPath("$.appHealth").value("UP"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "/api/events", "/admin", "/actuator/health"})
    void everythingExceptHealthRequiresAuthentication(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isForbidden());
    }

    @Test
    void healthDoesNotAcceptWrites() throws Exception {
        mvc.perform(post("/health").with(org.springframework.security.test.web.servlet.request
                        .SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isMethodNotAllowed());
    }
}
