package in.org.gatekeeper.api_management_service.config;

import in.org.gatekeeper.api_management_service.controller.GatewayConfigurationController;
import in.org.gatekeeper.api_management_service.controller.HealthController;
import in.org.gatekeeper.api_management_service.controller.PlatformOrganizationController;
import in.org.gatekeeper.api_management_service.controller.TenantRouteController;
import in.org.gatekeeper.api_management_service.service.HealthService;
import in.org.gatekeeper.api_management_service.service.OrganizationService;
import in.org.gatekeeper.api_management_service.service.RouteService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({HealthController.class, PlatformOrganizationController.class, TenantRouteController.class,
        GatewayConfigurationController.class})
@Import(SecurityConfig.class)
class SecurityConfigTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JwtEncoder jwtEncoder;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Value("${security.jwt.secret}")
    private String secret;

    @MockitoBean
    private RouteService routeService;
    @MockitoBean
    private OrganizationService organizationService;
    @MockitoBean
    private HealthService healthService;

    private String token(JwtEncoder encoder, String role, Long organizationId, Instant issuedAt, Instant expiresAt) {
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer("gatekeeper-api-management")
                .subject("someone")
                .issuedAt(issuedAt)
                .expiresAt(expiresAt);
        if (role != null) {
            claims.claim("role", role);
        }
        if (organizationId != null) {
            claims.claim("organizationId", organizationId);
        }
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims.build()))
                .getTokenValue();
    }

    private String validToken(String role) {
        return token(jwtEncoder, role, 7L, Instant.now(), Instant.now().plusSeconds(600));
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    @Test
    void passwordsAreHashedWithBCrypt() {
        assertThat(passwordEncoder).isInstanceOf(BCryptPasswordEncoder.class);
        String hash = passwordEncoder.encode("secret-123");
        assertThat(hash).startsWith("$2").isNotEqualTo("secret-123");
        assertThat(passwordEncoder.matches("secret-123", hash)).isTrue();
        assertThat(passwordEncoder.matches("other", hash)).isFalse();
    }

    @Nested
    class RoleClaimMapping {

        @Test
        void tenantAdminTokenOpensOnlyTheTenantArea() throws Exception {
            String token = validToken("TENANT_ADMIN");

            mvc.perform(get("/api/tenant/routes").header("Authorization", bearer(token))).andExpect(status().isOk());
            mvc.perform(get("/api/platform/organizations").header("Authorization", bearer(token)))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/api/gateway/routes").header("Authorization", bearer(token)))
                    .andExpect(status().isForbidden());
        }

        @Test
        void platformAdminTokenOpensOnlyThePlatformArea() throws Exception {
            String token = validToken("PLATFORM_ADMIN");

            mvc.perform(get("/api/platform/organizations").header("Authorization", bearer(token)))
                    .andExpect(status().isOk());
            mvc.perform(get("/api/tenant/routes").header("Authorization", bearer(token)))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/api/gateway/routes").header("Authorization", bearer(token)))
                    .andExpect(status().isForbidden());
        }

        @Test
        void gatewayTokenOpensOnlyTheGatewayArea() throws Exception {
            String token = validToken("GATEWAY_SERVICE");

            mvc.perform(get("/api/gateway/routes").header("Authorization", bearer(token))).andExpect(status().isOk());
            mvc.perform(get("/api/tenant/routes").header("Authorization", bearer(token)))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/api/platform/organizations").header("Authorization", bearer(token)))
                    .andExpect(status().isForbidden());
        }

        @Test
        void tokenWithoutRoleClaimGetsNoAccess() throws Exception {
            String token = token(jwtEncoder, null, 7L, Instant.now(), Instant.now().plusSeconds(600));

            mvc.perform(get("/api/tenant/routes").header("Authorization", bearer(token)))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/api/platform/organizations").header("Authorization", bearer(token)))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/api/gateway/routes").header("Authorization", bearer(token)))
                    .andExpect(status().isForbidden());
        }

        @ParameterizedTest
        @ValueSource(strings = {"SUPER_ADMIN", "tenant_admin", "ROLE_TENANT_ADMIN", ""})
        void tokenWithUnknownRoleGetsNoAccess(String role) throws Exception {
            String token = validToken(role);

            mvc.perform(get("/api/tenant/routes").header("Authorization", bearer(token)))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    class TokenValidation {

        @Test
        void expiredTokenIsRejectedWith401() throws Exception {
            String expired = token(jwtEncoder, "TENANT_ADMIN", 7L,
                    Instant.now().minusSeconds(7200), Instant.now().minusSeconds(3600));

            mvc.perform(get("/api/tenant/routes").header("Authorization", bearer(expired)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void tokenSignedWithAnotherSecretIsRejectedWith401() throws Exception {
            JwtEncoder foreign = NimbusJwtEncoder.withSecretKey(new SecretKeySpec(
                            "another-secret-another-secret-another-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
                    .build();
            String forged = token(foreign, "PLATFORM_ADMIN", null, Instant.now(), Instant.now().plusSeconds(600));

            mvc.perform(get("/api/platform/organizations").header("Authorization", bearer(forged)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void tamperedPayloadIsRejectedWith401() throws Exception {
            String genuine = validToken("TENANT_ADMIN");
            String[] parts = genuine.split("\\.");
            String forgedPayload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                    "{\"sub\":\"x\",\"role\":\"PLATFORM_ADMIN\",\"exp\":9999999999}".getBytes(StandardCharsets.UTF_8));
            String tampered = parts[0] + "." + forgedPayload + "." + parts[2];

            mvc.perform(get("/api/platform/organizations").header("Authorization", bearer(tampered)))
                    .andExpect(status().isUnauthorized());
        }

        @ParameterizedTest
        @ValueSource(strings = {"garbage", "a.b.c", "eyJhbGciOiJub25lIn0.e30.", ""})
        void malformedTokensAreRejectedWith401(String token) throws Exception {
            mvc.perform(get("/api/tenant/routes").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void nonBearerSchemesDoNotAuthenticate() throws Exception {
            mvc.perform(get("/api/tenant/routes").header("Authorization", "Basic dXNlcjpwYXNz"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void anonymousRequestsAreChallengedWithBearer() throws Exception {
            mvc.perform(get("/api/tenant/routes"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().exists("WWW-Authenticate"));
        }
    }

    @Nested
    class Rules {

        @Test
        void healthIsPublic() throws Exception {
            mvc.perform(get("/health")).andExpect(status().isOk());
        }

        @Test
        void unmappedPathsRequireAuthenticationBeforeBeingReportedMissing() throws Exception {
            mvc.perform(get("/api/unknown")).andExpect(status().isUnauthorized());
            mvc.perform(get("/api/unknown").header("Authorization", bearer(validToken("TENANT_ADMIN"))))
                    .andExpect(status().isNotFound());
        }

        @Test
        void csrfIsNotRequiredForBearerTokenWrites() throws Exception {
            mvc.perform(post("/api/platform/organizations")
                            .header("Authorization", bearer(validToken("PLATFORM_ADMIN")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Acme\",\"slug\":\"acme\"}"))
                    .andExpect(status().isCreated());
        }

        @Test
        void noHttpSessionIsCreated() throws Exception {
            mvc.perform(get("/api/tenant/routes").header("Authorization", bearer(validToken("TENANT_ADMIN"))))
                    .andExpect(status().isOk())
                    .andExpect(header().doesNotExist("Set-Cookie"));
        }
    }
}
