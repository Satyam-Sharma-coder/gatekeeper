package in.org.gatekeeper.api_management_service.service;

import in.org.gatekeeper.api_management_service.entity.ControlPlaneAuditRecord;
import in.org.gatekeeper.api_management_service.entity.Organization;
import in.org.gatekeeper.api_management_service.entity.OrganizationStatus;
import in.org.gatekeeper.api_management_service.repository.ControlPlaneAuditRecordRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.List;

import static in.org.gatekeeper.api_management_service.support.TestData.organization;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private ControlPlaneAuditRecordRepository repository;

    @AfterEach
    void clearContexts() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    private ControlPlaneAuditRecord record(Organization organization, long startedAt) {
        new AuditService(repository).record(organization, "CREATE_ROUTE", "API_ROUTE", "orders", startedAt, "details");
        ArgumentCaptor<ControlPlaneAuditRecord> captor = ArgumentCaptor.forClass(ControlPlaneAuditRecord.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void recordsActorFromTheAuthenticatedUser() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("acme-admin", "n/a", List.of()));
        Organization organization = organization(7L, "acme", OrganizationStatus.ACTIVE);
        LocalDateTime before = LocalDateTime.now();

        ControlPlaneAuditRecord saved = record(organization, System.nanoTime());

        assertThat(saved.getActor()).isEqualTo("acme-admin");
        assertThat(saved.getOrganization()).isSameAs(organization);
        assertThat(saved.getAction()).isEqualTo("CREATE_ROUTE");
        assertThat(saved.getResourceType()).isEqualTo("API_ROUTE");
        assertThat(saved.getResourceId()).isEqualTo("orders");
        assertThat(saved.getDetails()).isEqualTo("details");
        assertThat(saved.getResult()).isEqualTo("SUCCESS");
        assertThat(saved.getCreatedAt()).isAfterOrEqualTo(before);
    }

    @Test
    void fallsBackToSystemActorWithoutAuthentication() {
        ControlPlaneAuditRecord saved = record(null, System.nanoTime());

        assertThat(saved.getActor()).isEqualTo("system");
        assertThat(saved.getOrganization()).isNull();
    }

    @Test
    void storesCorrelationIdFromTheRequestHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Correlation-ID", "corr-123");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertThat(record(null, System.nanoTime()).getCorrelationId()).isEqualTo("corr-123");
    }

    @Test
    void leavesCorrelationIdEmptyWhenHeaderIsAbsent() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));

        assertThat(record(null, System.nanoTime()).getCorrelationId()).isNull();
    }

    @Test
    void leavesCorrelationIdEmptyOutsideARequest() {
        assertThat(record(null, System.nanoTime()).getCorrelationId()).isNull();
    }

    @Test
    void measuresDurationInMilliseconds() {
        long startedFiveHundredMillisAgo = System.nanoTime() - 500_000_000L;

        ControlPlaneAuditRecord saved = record(null, startedFiveHundredMillisAgo);

        assertThat(saved.getDurationMs()).isBetween(500L, 5_000L);
    }
}
