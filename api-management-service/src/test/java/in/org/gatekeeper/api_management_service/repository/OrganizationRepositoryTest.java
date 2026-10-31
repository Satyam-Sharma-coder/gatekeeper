package in.org.gatekeeper.api_management_service.repository;

import in.org.gatekeeper.api_management_service.entity.Organization;
import in.org.gatekeeper.api_management_service.entity.OrganizationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(showSql = false)
class OrganizationRepositoryTest {

    @Autowired
    private OrganizationRepository repository;

    private Organization active;
    private Organization deleted;

    private Organization save(String name, String slug, OrganizationStatus status) {
        return repository.save(Organization.builder()
                .name(name).slug(slug).status(status)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build());
    }

    @BeforeEach
    void seed() {
        active = save("Acme Corp", "acme-corp", OrganizationStatus.ACTIVE);
        save("Pending Inc", "pending-inc", OrganizationStatus.PENDING);
        deleted = save("Gone Ltd", "gone-ltd", OrganizationStatus.DELETED);
    }

    @Test
    void existsByNameIgnoreCaseMatchesAnyCase() {
        assertThat(repository.existsByNameIgnoreCase("acme corp")).isTrue();
        assertThat(repository.existsByNameIgnoreCase("ACME CORP")).isTrue();
        assertThat(repository.existsByNameIgnoreCase("Acme")).isFalse();
    }

    @Test
    void existsBySlugIgnoreCaseMatchesAnyCase() {
        assertThat(repository.existsBySlugIgnoreCase("ACME-CORP")).isTrue();
        assertThat(repository.existsBySlugIgnoreCase("acme")).isFalse();
    }

    @Test
    void deletedOrganizationsStillOccupyNameAndSlug() {
        assertThat(repository.existsByNameIgnoreCase("gone ltd")).isTrue();
        assertThat(repository.existsBySlugIgnoreCase("gone-ltd")).isTrue();
    }

    @Test
    void findAllByStatusNotExcludesDeleted() {
        assertThat(repository.findAllByStatusNot(OrganizationStatus.DELETED))
                .extracting(Organization::getSlug)
                .containsExactlyInAnyOrder("acme-corp", "pending-inc");
    }

    @Test
    void findByIdAndStatusNotReturnsVisibleOrganization() {
        assertThat(repository.findByIdAndStatusNot(active.getId(), OrganizationStatus.DELETED)).isPresent();
    }

    @Test
    void findByIdAndStatusNotHidesDeletedOrganization() {
        assertThat(repository.findByIdAndStatusNot(deleted.getId(), OrganizationStatus.DELETED)).isEmpty();
    }

    @Test
    void statusIsPersistedAsText() {
        assertThat(repository.findById(active.getId()).orElseThrow().getStatus()).isEqualTo(OrganizationStatus.ACTIVE);
    }
}
