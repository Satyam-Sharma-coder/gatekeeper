package in.org.gatekeeper.api_management_service.service;

import in.org.gatekeeper.api_management_service.dto.request.LoginRequest;
import in.org.gatekeeper.api_management_service.dto.response.LoginResponse;
import in.org.gatekeeper.api_management_service.entity.ManagementUser;
import in.org.gatekeeper.api_management_service.entity.Organization;
import in.org.gatekeeper.api_management_service.entity.OrganizationStatus;
import in.org.gatekeeper.api_management_service.entity.Role;
import in.org.gatekeeper.api_management_service.exception.ApiException;
import in.org.gatekeeper.api_management_service.repository.ManagementUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import java.time.Instant;
import java.util.Optional;

import static in.org.gatekeeper.api_management_service.support.ApiExceptionAssertions.assertApiException;
import static in.org.gatekeeper.api_management_service.support.TestData.organization;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    private static final long EXPIRATION_SECONDS = 3600L;

    @Mock
    private ManagementUserRepository managementUserRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtEncoder jwtEncoder;

    private AuthenticationService service;

    @BeforeEach
    void setUp() {
        service = new AuthenticationService(managementUserRepository, passwordEncoder, jwtEncoder, EXPIRATION_SECONDS);
    }

    private static LoginRequest loginRequest(String username, String password) {
        LoginRequest request = new LoginRequest();
        request.setUsername(username);
        request.setPassword(password);
        return request;
    }

    private static ManagementUser user(String username, String roleName, Organization organization, boolean enabled) {
        Role role = new Role();
        role.setId(1L);
        role.setName(roleName);
        return ManagementUser.builder()
                .id(10L).username(username).passwordHash("stored-hash").enabled(enabled)
                .role(role).organization(organization)
                .build();
    }

    private void stubSigning() {
        when(jwtEncoder.encode(any(JwtEncoderParameters.class))).thenReturn(
                Jwt.withTokenValue("signed-token").header("alg", "HS256").claim("sub", "x").build());
    }

    private JwtClaimsSet capturedClaims() {
        ArgumentCaptor<JwtEncoderParameters> captor = ArgumentCaptor.forClass(JwtEncoderParameters.class);
        verify(jwtEncoder).encode(captor.capture());
        return captor.getValue().getClaims();
    }

    @Test
    void tenantAdminReceivesTokenCarryingRoleAndOrganization() {
        Organization organization = organization(7L, "acme", OrganizationStatus.ACTIVE);
        when(managementUserRepository.findByUsername("acme-admin"))
                .thenReturn(Optional.of(user("acme-admin", "TENANT_ADMIN", organization, true)));
        when(passwordEncoder.matches("secret", "stored-hash")).thenReturn(true);
        stubSigning();

        Instant before = Instant.now();
        LoginResponse response = service.login(loginRequest("acme-admin", "secret"));
        Instant after = Instant.now();

        assertThat(response.getToken()).isEqualTo("signed-token");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getExpiresInSeconds()).isEqualTo(EXPIRATION_SECONDS);
        assertThat(response.getRole()).isEqualTo("TENANT_ADMIN");
        assertThat(response.getOrganizationId()).isEqualTo(7L);

        JwtClaimsSet claims = capturedClaims();
        assertThat(claims.getClaims().get("iss")).hasToString("gatekeeper-api-management");
        assertThat(claims.getClaims().get("sub")).isEqualTo("acme-admin");
        assertThat(claims.getClaims().get("role")).isEqualTo("TENANT_ADMIN");
        assertThat(claims.getClaims().get("organizationId")).isEqualTo(7L);
        Instant issuedAt = (Instant) claims.getClaims().get("iat");
        Instant expiresAt = (Instant) claims.getClaims().get("exp");
        assertThat(issuedAt).isBetween(before, after);
        assertThat(expiresAt).isEqualTo(issuedAt.plusSeconds(EXPIRATION_SECONDS));
    }

    @Test
    void platformAdminTokenHasNoOrganizationClaim() {
        when(managementUserRepository.findByUsername("platform-admin"))
                .thenReturn(Optional.of(user("platform-admin", "PLATFORM_ADMIN", null, true)));
        when(passwordEncoder.matches("secret", "stored-hash")).thenReturn(true);
        stubSigning();

        LoginResponse response = service.login(loginRequest("platform-admin", "secret"));

        assertThat(response.getRole()).isEqualTo("PLATFORM_ADMIN");
        assertThat(response.getOrganizationId()).isNull();
        assertThat(capturedClaims().getClaims()).doesNotContainKey("organizationId");
    }

    @Test
    void gatewayAccountCanSignInWithoutAnOrganization() {
        when(managementUserRepository.findByUsername("gateway-service"))
                .thenReturn(Optional.of(user("gateway-service", "GATEWAY_SERVICE", null, true)));
        when(passwordEncoder.matches("secret", "stored-hash")).thenReturn(true);
        stubSigning();

        assertThat(service.login(loginRequest("gateway-service", "secret")).getRole()).isEqualTo("GATEWAY_SERVICE");
    }

    @Test
    void unknownUserIsRejectedWithGenericMessage() {
        when(managementUserRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertApiException(() -> service.login(loginRequest("ghost", "secret")),
                HttpStatus.UNAUTHORIZED, "Invalid username or password");

        verifyNoInteractions(jwtEncoder, passwordEncoder);
    }

    @Test
    void disabledUserIsRejectedWithTheSameMessageAsWrongPassword() {
        Organization organization = organization(7L, "acme", OrganizationStatus.ACTIVE);
        when(managementUserRepository.findByUsername("acme-admin"))
                .thenReturn(Optional.of(user("acme-admin", "TENANT_ADMIN", organization, false)));

        assertApiException(() -> service.login(loginRequest("acme-admin", "secret")),
                HttpStatus.UNAUTHORIZED, "Invalid username or password");

        verifyNoInteractions(jwtEncoder);
    }

    @Test
    void wrongPasswordIsRejected() {
        Organization organization = organization(7L, "acme", OrganizationStatus.ACTIVE);
        when(managementUserRepository.findByUsername("acme-admin"))
                .thenReturn(Optional.of(user("acme-admin", "TENANT_ADMIN", organization, true)));
        when(passwordEncoder.matches("wrong", "stored-hash")).thenReturn(false);

        assertApiException(() -> service.login(loginRequest("acme-admin", "wrong")),
                HttpStatus.UNAUTHORIZED, "Invalid username or password");

        verifyNoInteractions(jwtEncoder);
    }

    @ParameterizedTest
    @EnumSource(value = OrganizationStatus.class, names = {"PENDING", "SUSPENDED", "DELETED"})
    void userOfAnInactiveOrganizationIsForbidden(OrganizationStatus status) {
        Organization organization = organization(7L, "acme", status);
        when(managementUserRepository.findByUsername("acme-admin"))
                .thenReturn(Optional.of(user("acme-admin", "TENANT_ADMIN", organization, true)));
        when(passwordEncoder.matches("secret", "stored-hash")).thenReturn(true);

        assertApiException(() -> service.login(loginRequest("acme-admin", "secret")),
                HttpStatus.FORBIDDEN, "not active");

        verify(jwtEncoder, never()).encode(any());
    }

    @Test
    void passwordIsCheckedBeforeTheOrganizationStatusIsRevealed() {
        Organization organization = organization(7L, "acme", OrganizationStatus.SUSPENDED);
        when(managementUserRepository.findByUsername("acme-admin"))
                .thenReturn(Optional.of(user("acme-admin", "TENANT_ADMIN", organization, true)));
        when(passwordEncoder.matches("wrong", "stored-hash")).thenReturn(false);

        assertThatThrownBy(() -> service.login(loginRequest("acme-admin", "wrong")))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }
}
