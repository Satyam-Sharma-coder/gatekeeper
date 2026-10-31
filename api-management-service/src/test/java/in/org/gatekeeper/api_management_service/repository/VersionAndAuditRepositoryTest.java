package in.org.gatekeeper.api_management_service.repository;

import in.org.gatekeeper.api_management_service.entity.ControlPlaneAuditRecord;
import in.org.gatekeeper.api_management_service.entity.Organization;
import in.org.gatekeeper.api_management_service.entity.OrganizationStatus;
import in.org.gatekeeper.api_management_service.entity.RouteConfigurationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(showSql = false)
class VersionAndAuditRepositoryTest {

    @Autowired
    private RouteConfigurationVersionRepository versions;
    @Autowired
    private ControlPlaneAuditRecordRepository audits;
    @Autowired
    private OrganizationRepository organizations;

    private Organization acme;
    private Organization globex;

    @BeforeEach
    void seed() {
        acme = organizations.save(Organization.builder().name("Acme").slug("acme").status(OrganizationStatus.ACTIVE)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        globex = organizations.save(Organization.builder().name("Globex").slug("globex").status(OrganizationStatus.ACTIVE)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
    }

    private void version(Organization organization, String routeId, long number, String type) {
        RouteConfigurationVersion version = new RouteConfigurationVersion();
        version.setOrganization(organization);
        version.setRouteId(routeId);
        version.setVersionNumber(number);
        version.setChangeType(type);
        version.setConfigurationSnapshot("{\"v\":" + number + "}");
        version.setCreatedAt(LocalDateTime.now());
        versions.save(version);
    }

    private void audit(Organization organization, String action, LocalDateTime at) {
        ControlPlaneAuditRecord record = new ControlPlaneAuditRecord();
        record.setOrganization(organization);
        record.setActor("actor");
        record.setAction(action);
        record.setResourceType("API_ROUTE");
        record.setResourceId("orders");
        record.setDurationMs(1L);
        record.setResult("SUCCESS");
        record.setCreatedAt(at);
        audits.save(record);
    }

    @Test
    void versionsAreReturnedNewestFirst() {
        version(acme, "orders", 1, "CREATED");
        version(acme, "orders", 3, "DEACTIVATED");
        version(acme, "orders", 2, "ACTIVATED");

        assertThat(versions.findAllByOrganizationIdAndRouteIdOrderByVersionNumberDesc(acme.getId(), "orders"))
                .extracting(RouteConfigurationVersion::getVersionNumber)
                .containsExactly(3L, 2L, 1L);
    }

    @Test
    void versionsAreScopedByRouteId() {
        version(acme, "orders", 1, "CREATED");
        version(acme, "billing", 1, "CREATED");
        version(acme, "billing", 2, "UPDATED");

        assertThat(versions.findAllByOrganizationIdAndRouteIdOrderByVersionNumberDesc(acme.getId(), "billing"))
                .hasSize(2);
        assertThat(versions.findAllByOrganizationIdAndRouteIdOrderByVersionNumberDesc(acme.getId(), "orders"))
                .hasSize(1);
    }

    @Test
    void versionsOfAnotherOrganizationWithTheSameRouteIdAreNotVisible() {
        version(acme, "orders", 1, "CREATED");
        version(globex, "orders", 1, "CREATED");
        version(globex, "orders", 2, "UPDATED");

        assertThat(versions.findAllByOrganizationIdAndRouteIdOrderByVersionNumberDesc(acme.getId(), "orders"))
                .hasSize(1);
        assertThat(versions.findAllByOrganizationIdAndRouteIdOrderByVersionNumberDesc(globex.getId(), "orders"))
                .hasSize(2);
    }

    @Test
    void unknownRouteHasNoVersions() {
        assertThat(versions.findAllByOrganizationIdAndRouteIdOrderByVersionNumberDesc(acme.getId(), "nope")).isEmpty();
    }

    @Test
    void largeSnapshotsAreStoredCompletely() {
        RouteConfigurationVersion version = new RouteConfigurationVersion();
        version.setOrganization(acme);
        version.setRouteId("big");
        version.setVersionNumber(1L);
        version.setChangeType("CREATED");
        version.setConfigurationSnapshot("x".repeat(100_000));
        version.setCreatedAt(LocalDateTime.now());
        versions.saveAndFlush(version);

        assertThat(versions.findAllByOrganizationIdAndRouteIdOrderByVersionNumberDesc(acme.getId(), "big").get(0)
                .getConfigurationSnapshot()).hasSize(100_000);
    }

    @Test
    void auditRecordsAreReturnedNewestFirst() {
        LocalDateTime base = LocalDateTime.of(2026, 10, 29, 10, 0);
        audit(acme, "CREATE_ROUTE", base);
        audit(acme, "DELETE_ROUTE", base.plusHours(2));
        audit(acme, "UPDATE_ROUTE", base.plusHours(1));

        assertThat(audits.findAllByOrganizationIdOrderByCreatedAtDesc(acme.getId()))
                .extracting(ControlPlaneAuditRecord::getAction)
                .containsExactly("DELETE_ROUTE", "UPDATE_ROUTE", "CREATE_ROUTE");
    }

    @Test
    void auditRecordsOfOtherOrganizationsAreNotVisible() {
        audit(acme, "CREATE_ROUTE", LocalDateTime.now());
        audit(globex, "CREATE_ROUTE", LocalDateTime.now());
        audit(globex, "DELETE_ROUTE", LocalDateTime.now());

        assertThat(audits.findAllByOrganizationIdOrderByCreatedAtDesc(acme.getId())).hasSize(1);
        assertThat(audits.findAllByOrganizationIdOrderByCreatedAtDesc(globex.getId())).hasSize(2);
    }

    @Test
    void auditRecordsKeepAllFields() {
        ControlPlaneAuditRecord record = new ControlPlaneAuditRecord();
        record.setOrganization(acme);
        record.setActor("acme-admin");
        record.setAction("CREATE_ROUTE");
        record.setResourceType("API_ROUTE");
        record.setResourceId("orders");
        record.setCorrelationId("corr-1");
        record.setDurationMs(15L);
        record.setResult("SUCCESS");
        record.setDetails("Route configuration version 1 created");
        record.setCreatedAt(LocalDateTime.now());
        audits.saveAndFlush(record);

        ControlPlaneAuditRecord loaded = audits.findAllByOrganizationIdOrderByCreatedAtDesc(acme.getId()).get(0);

        assertThat(loaded.getActor()).isEqualTo("acme-admin");
        assertThat(loaded.getCorrelationId()).isEqualTo("corr-1");
        assertThat(loaded.getDurationMs()).isEqualTo(15L);
        assertThat(loaded.getDetails()).isEqualTo("Route configuration version 1 created");
    }
}
