package in.org.gatekeeper.api_management_service.service;

import in.org.gatekeeper.api_management_service.entity.Organization;
import in.org.gatekeeper.api_management_service.entity.OrganizationStatus;
import in.org.gatekeeper.api_management_service.repository.OrganizationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;
import java.util.Optional;

import static in.org.gatekeeper.api_management_service.support.ApiExceptionAssertions.assertApiException;
import static in.org.gatekeeper.api_management_service.support.TestData.organization;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantContextTest {

    @Mock
    private OrganizationRepository organizationRepository;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private TenantContext tenantContext() {
        return new TenantContext(organizationRepository);
    }

    private void authenticateWithClaims(Jwt.Builder builder) {
        Jwt jwt = builder.header("alg", "HS256").subject("acme-admin").build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_TENANT_ADMIN"))));
    }

    @Test
    void rejectsRequestsWithoutAuthentication() {
        assertApiException(() -> tenantContext().getOrganization(), HttpStatus.FORBIDDEN, "Tenant identity is missing");

        verifyNoInteractions(organizationRepository);
    }

    @Test
    void rejectsAuthenticationThatIsNotAJwt() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("someone", "n/a", List.of()));

        assertApiException(() -> tenantContext().getOrganization(), HttpStatus.FORBIDDEN, "Tenant identity is missing");

        verifyNoInteractions(organizationRepository);
    }

    @Test
    void rejectsJwtWithoutOrganizationClaim() {
        authenticateWithClaims(Jwt.withTokenValue("t").claim("role", "TENANT_ADMIN"));

        assertApiException(() -> tenantContext().getOrganization(), HttpStatus.FORBIDDEN, "Tenant identity is missing");

        verifyNoInteractions(organizationRepository);
    }

    @Test
    void rejectsOrganizationThatNoLongerExists() {
        authenticateWithClaims(Jwt.withTokenValue("t").claim("organizationId", 7L));
        when(organizationRepository.findById(7L)).thenReturn(Optional.empty());

        assertApiException(() -> tenantContext().getOrganization(), HttpStatus.FORBIDDEN, "does not exist");
    }

    @ParameterizedTest
    @EnumSource(value = OrganizationStatus.class, names = {"PENDING", "SUSPENDED", "DELETED"})
    void rejectsOrganizationThatIsNotActive(OrganizationStatus status) {
        authenticateWithClaims(Jwt.withTokenValue("t").claim("organizationId", 7L));
        when(organizationRepository.findById(7L)).thenReturn(Optional.of(organization(7L, "acme", status)));

        assertApiException(() -> tenantContext().getOrganization(), HttpStatus.FORBIDDEN, "not active");
    }

    @Test
    void returnsTheActiveOrganizationNamedInTheToken() {
        Organization active = organization(7L, "acme", OrganizationStatus.ACTIVE);
        authenticateWithClaims(Jwt.withTokenValue("t").claim("organizationId", 7L));
        when(organizationRepository.findById(7L)).thenReturn(Optional.of(active));

        assertThat(tenantContext().getOrganization()).isSameAs(active);
    }

    @Test
    void usesOnlyTheTokenClaimAndIgnoresAnyOtherOrganization() {
        Organization mine = organization(7L, "acme", OrganizationStatus.ACTIVE);
        authenticateWithClaims(Jwt.withTokenValue("t").claim("organizationId", 7L).claim("organization", 8L));
        when(organizationRepository.findById(7L)).thenReturn(Optional.of(mine));

        assertThat(tenantContext().getOrganization().getId()).isEqualTo(7L);
    }
}
