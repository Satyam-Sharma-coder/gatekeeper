package in.org.gatekeeper.api_management_service.repository;

import in.org.gatekeeper.api_management_service.entity.ManagementUser;
import in.org.gatekeeper.api_management_service.entity.Organization;
import in.org.gatekeeper.api_management_service.entity.OrganizationStatus;
import in.org.gatekeeper.api_management_service.entity.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(showSql = false)
class ManagementUserRepositoryTest {

    @Autowired
    private ManagementUserRepository users;
    @Autowired
    private OrganizationRepository organizations;
    @Autowired
    private RoleRepository roles;

    private Organization acme;
    private Role tenantRole;

    @BeforeEach
    void seed() {
        acme = organizations.save(Organization.builder().name("Acme").slug("acme").status(OrganizationStatus.ACTIVE)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());

        tenantRole = newRole("TENANT_ADMIN");
        Role platformRole = newRole("PLATFORM_ADMIN");

        users.save(user("acme-admin", tenantRole, acme));
        users.save(user("platform-admin", platformRole, null));
    }

    private Role newRole(String name) {
        Role role = new Role();
        role.setName(name);
        return roles.save(role);
    }

    private ManagementUser user(String username, Role role, Organization organization) {
        return ManagementUser.builder()
                .username(username).passwordHash("hash").enabled(true).role(role).organization(organization)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void findByUsernameReturnsUserWithRole() {
        ManagementUser found = users.findByUsername("acme-admin").orElseThrow();

        assertThat(found.getRole().getName()).isEqualTo("TENANT_ADMIN");
        assertThat(found.getOrganization().getId()).isEqualTo(acme.getId());
        assertThat(found.isEnabled()).isTrue();
    }

    @Test
    void findByUsernameIsEmptyForUnknownUser() {
        assertThat(users.findByUsername("nobody")).isEmpty();
    }

    @Test
    void platformAccountsHaveNoOrganization() {
        assertThat(users.findByUsername("platform-admin").orElseThrow().getOrganization()).isNull();
    }

    @Test
    void findByOrganizationIdReturnsTheTenantAdmin() {
        assertThat(users.findByOrganizationId(acme.getId())).get()
                .extracting(ManagementUser::getUsername).isEqualTo("acme-admin");
    }

    @Test
    void findByOrganizationIdIsEmptyForOrganizationWithoutAdmin() {
        Organization other = organizations.save(Organization.builder().name("Other").slug("other")
                .status(OrganizationStatus.ACTIVE).createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());

        assertThat(users.findByOrganizationId(other.getId())).isEmpty();
    }

    @Test
    void existsByUsernameIsCaseSensitiveExactMatch() {
        assertThat(users.existsByUsername("acme-admin")).isTrue();
        assertThat(users.existsByUsername("platform-admin")).isTrue();
        assertThat(users.existsByUsername("missing")).isFalse();
    }

    @Test
    void rolesAreFoundByName() {
        assertThat(roles.findByName("TENANT_ADMIN")).get().extracting(Role::getId).isEqualTo(tenantRole.getId());
        assertThat(roles.findByName("GATEWAY_SERVICE")).isEmpty();
    }
}
