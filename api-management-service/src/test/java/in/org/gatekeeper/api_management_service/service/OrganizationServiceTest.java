package in.org.gatekeeper.api_management_service.service;

import in.org.gatekeeper.api_management_service.dto.request.OrganizationRequest;
import in.org.gatekeeper.api_management_service.dto.request.OrganizationStatusRequest;
import in.org.gatekeeper.api_management_service.dto.request.TenantAdminRequest;
import in.org.gatekeeper.api_management_service.dto.request.TenantOrganizationRequest;
import in.org.gatekeeper.api_management_service.dto.response.OrganizationResponse;
import in.org.gatekeeper.api_management_service.dto.response.TenantAdminResponse;
import in.org.gatekeeper.api_management_service.entity.ManagementUser;
import in.org.gatekeeper.api_management_service.entity.Organization;
import in.org.gatekeeper.api_management_service.entity.OrganizationStatus;
import in.org.gatekeeper.api_management_service.entity.Role;
import in.org.gatekeeper.api_management_service.repository.ManagementUserRepository;
import in.org.gatekeeper.api_management_service.repository.OrganizationRepository;
import in.org.gatekeeper.api_management_service.repository.RoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static in.org.gatekeeper.api_management_service.support.ApiExceptionAssertions.assertApiException;
import static in.org.gatekeeper.api_management_service.support.TestData.organization;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganizationServiceTest {

    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private ManagementUserRepository managementUserRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private TenantContext tenantContext;
    @Mock
    private AuditService auditService;

    private OrganizationService service;

    @BeforeEach
    void setUp() {
        service = new OrganizationService(organizationRepository, managementUserRepository, roleRepository,
                passwordEncoder, tenantContext, auditService);
    }

    private static OrganizationRequest organizationRequest(String name, String slug) {
        OrganizationRequest request = new OrganizationRequest();
        request.setName(name);
        request.setSlug(slug);
        return request;
    }

    private static TenantAdminRequest tenantAdminRequest(String username, String password, boolean enabled) {
        TenantAdminRequest request = new TenantAdminRequest();
        request.setUsername(username);
        request.setPassword(password);
        request.setEnabled(enabled);
        return request;
    }

    @Nested
    class CreateOrganization {

        @Test
        void savesPendingOrganizationAndAudits() {
            when(organizationRepository.save(any(Organization.class))).thenAnswer(invocation -> {
                Organization saved = invocation.getArgument(0);
                saved.setId(7L);
                return saved;
            });

            OrganizationResponse response = service.create(organizationRequest("Acme Corp", "acme-corp"));

            ArgumentCaptor<Organization> captor = ArgumentCaptor.forClass(Organization.class);
            verify(organizationRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(OrganizationStatus.PENDING);
            assertThat(captor.getValue().getName()).isEqualTo("Acme Corp");
            assertThat(captor.getValue().getSlug()).isEqualTo("acme-corp");
            assertThat(captor.getValue().getCreatedAt()).isNotNull();
            assertThat(captor.getValue().getUpdatedAt()).isNotNull();

            assertThat(response.getId()).isEqualTo(7L);
            assertThat(response.getStatus()).isEqualTo(OrganizationStatus.PENDING);
            verify(auditService).record(any(Organization.class), eq("CREATE_ORGANIZATION"), eq("ORGANIZATION"),
                    eq("7"), anyLong(), eq("Organization created in PENDING status"));
        }

        @Test
        void rejectsDuplicateNameIgnoringCase() {
            when(organizationRepository.existsByNameIgnoreCase("ACME CORP")).thenReturn(true);

            assertApiException(() -> service.create(organizationRequest("ACME CORP", "acme-corp")),
                    HttpStatus.CONFLICT, "name");

            verify(organizationRepository, never()).save(any());
            verifyNoInteractions(auditService);
        }

        @Test
        void rejectsDuplicateSlugIgnoringCase() {
            when(organizationRepository.existsBySlugIgnoreCase("acme-corp")).thenReturn(true);

            assertApiException(() -> service.create(organizationRequest("Acme Corp", "acme-corp")),
                    HttpStatus.CONFLICT, "slug");

            verify(organizationRepository, never()).save(any());
        }
    }

    @Nested
    class ReadOrganizations {

        @Test
        void findAllExcludesDeletedOrganizations() {
            when(organizationRepository.findAllByStatusNot(OrganizationStatus.DELETED)).thenReturn(List.of(
                    organization(1L, "a", OrganizationStatus.ACTIVE),
                    organization(2L, "b", OrganizationStatus.PENDING)));

            List<OrganizationResponse> result = service.findAll();

            assertThat(result).extracting(OrganizationResponse::getSlug).containsExactly("a", "b");
        }

        @Test
        void findByIdReturnsOrganization() {
            when(organizationRepository.findByIdAndStatusNot(1L, OrganizationStatus.DELETED))
                    .thenReturn(Optional.of(organization(1L, "a", OrganizationStatus.ACTIVE)));

            assertThat(service.findById(1L).getName()).isEqualTo("Org a");
        }

        @Test
        void findByIdHidesDeletedAndMissingOrganizations() {
            when(organizationRepository.findByIdAndStatusNot(1L, OrganizationStatus.DELETED))
                    .thenReturn(Optional.empty());

            assertApiException(() -> service.findById(1L), HttpStatus.NOT_FOUND, "Organization not found");
        }
    }

    @Nested
    class UpdateOrganization {

        private Organization existing;

        @BeforeEach
        void givenExistingOrganization() {
            existing = organization(1L, "acme", OrganizationStatus.ACTIVE);
            existing.setName("Acme");
            when(organizationRepository.findByIdAndStatusNot(1L, OrganizationStatus.DELETED))
                    .thenReturn(Optional.of(existing));
        }

        @Test
        void updatesNameAndSlugAndAudits() {
            service.update(1L, organizationRequest("Acme Renamed", "acme-renamed"));

            assertThat(existing.getName()).isEqualTo("Acme Renamed");
            assertThat(existing.getSlug()).isEqualTo("acme-renamed");
            verify(auditService).record(eq(existing), eq("UPDATE_ORGANIZATION"), eq("ORGANIZATION"), eq("1"),
                    anyLong(), eq("Organization name and slug updated"));
        }

        @Test
        void allowsKeepingItsOwnNameAndSlugWithDifferentCase() {
            when(organizationRepository.existsByNameIgnoreCase("ACME")).thenReturn(true);
            when(organizationRepository.existsBySlugIgnoreCase("acme")).thenReturn(true);
            when(organizationRepository.findById(1L)).thenReturn(Optional.of(existing));

            service.update(1L, organizationRequest("ACME", "acme"));

            assertThat(existing.getName()).isEqualTo("ACME");
        }

        @Test
        void rejectsNameUsedByAnotherOrganization() {
            when(organizationRepository.existsByNameIgnoreCase("Globex")).thenReturn(true);
            when(organizationRepository.findById(1L)).thenReturn(Optional.of(existing));

            assertApiException(() -> service.update(1L, organizationRequest("Globex", "acme")),
                    HttpStatus.CONFLICT);

            assertThat(existing.getName()).isEqualTo("Acme");
            verifyNoInteractions(auditService);
        }

        @Test
        void rejectsSlugUsedByAnotherOrganization() {
            when(organizationRepository.existsBySlugIgnoreCase("globex")).thenReturn(true);
            when(organizationRepository.findById(1L)).thenReturn(Optional.of(existing));

            assertApiException(() -> service.update(1L, organizationRequest("Acme", "globex")),
                    HttpStatus.CONFLICT);

            assertThat(existing.getSlug()).isEqualTo("acme");
        }
    }

    @Nested
    class ChangeStatus {

        @Test
        void setsAllowedStatusesAndAudits() {
            Organization existing = organization(1L, "acme", OrganizationStatus.PENDING);
            when(organizationRepository.findByIdAndStatusNot(1L, OrganizationStatus.DELETED))
                    .thenReturn(Optional.of(existing));
            OrganizationStatusRequest request = new OrganizationStatusRequest();
            request.setStatus(OrganizationStatus.SUSPENDED);

            OrganizationResponse response = service.changeStatus(1L, request);

            assertThat(existing.getStatus()).isEqualTo(OrganizationStatus.SUSPENDED);
            assertThat(response.getStatus()).isEqualTo(OrganizationStatus.SUSPENDED);
            verify(auditService).record(eq(existing), eq("CHANGE_ORGANIZATION_STATUS"), eq("ORGANIZATION"), eq("1"),
                    anyLong(), eq("Status changed to SUSPENDED"));
        }

        @Test
        void refusesDeletedStatusAndPointsToDelete() {
            Organization existing = organization(1L, "acme", OrganizationStatus.ACTIVE);
            when(organizationRepository.findByIdAndStatusNot(1L, OrganizationStatus.DELETED))
                    .thenReturn(Optional.of(existing));
            OrganizationStatusRequest request = new OrganizationStatusRequest();
            request.setStatus(OrganizationStatus.DELETED);

            assertApiException(() -> service.changeStatus(1L, request), HttpStatus.BAD_REQUEST, "DELETE");

            assertThat(existing.getStatus()).isEqualTo(OrganizationStatus.ACTIVE);
            verifyNoInteractions(auditService);
        }

        @Test
        void reportsMissingOrganization() {
            when(organizationRepository.findByIdAndStatusNot(1L, OrganizationStatus.DELETED))
                    .thenReturn(Optional.empty());
            OrganizationStatusRequest request = new OrganizationStatusRequest();
            request.setStatus(OrganizationStatus.ACTIVE);

            assertApiException(() -> service.changeStatus(1L, request), HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    class DeleteOrganization {

        @Test
        void softDeletesByMarkingStatusAndTimestamp() {
            Organization existing = organization(1L, "acme", OrganizationStatus.ACTIVE);
            when(organizationRepository.findByIdAndStatusNot(1L, OrganizationStatus.DELETED))
                    .thenReturn(Optional.of(existing));

            service.delete(1L);

            assertThat(existing.getStatus()).isEqualTo(OrganizationStatus.DELETED);
            assertThat(existing.getDeletedAt()).isNotNull();
            verify(organizationRepository, never()).delete(any());
            verify(auditService).record(eq(existing), eq("DELETE_ORGANIZATION"), eq("ORGANIZATION"), eq("1"),
                    anyLong(), eq("Organization soft-deleted"));
        }

        @Test
        void reportsMissingOrganization() {
            when(organizationRepository.findByIdAndStatusNot(1L, OrganizationStatus.DELETED))
                    .thenReturn(Optional.empty());

            assertApiException(() -> service.delete(1L), HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    class TenantAdminManagement {

        private Organization organization;

        @BeforeEach
        void givenOrganization() {
            organization = organization(1L, "acme", OrganizationStatus.ACTIVE);
            when(organizationRepository.findByIdAndStatusNot(1L, OrganizationStatus.DELETED))
                    .thenReturn(Optional.of(organization));
        }

        private ManagementUser existingAdmin() {
            Role role = new Role();
            role.setId(2L);
            role.setName("TENANT_ADMIN");
            return ManagementUser.builder()
                    .id(3L).organization(organization).role(role)
                    .username("acme-admin").passwordHash("old-hash").enabled(true)
                    .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                    .build();
        }

        @Test
        void createsAdminWithHashedPasswordAndTenantRole() {
            Role role = new Role();
            role.setId(2L);
            role.setName("TENANT_ADMIN");
            when(managementUserRepository.findByOrganizationId(1L)).thenReturn(Optional.empty());
            when(managementUserRepository.existsByUsername("acme-admin")).thenReturn(false);
            when(roleRepository.findByName("TENANT_ADMIN")).thenReturn(Optional.of(role));
            when(passwordEncoder.encode("secret-123")).thenReturn("hashed");
            when(managementUserRepository.save(any(ManagementUser.class))).thenAnswer(invocation -> {
                ManagementUser saved = invocation.getArgument(0);
                saved.setId(3L);
                return saved;
            });

            TenantAdminResponse response = service.createTenantAdmin(1L, tenantAdminRequest("acme-admin", "secret-123", true));

            ArgumentCaptor<ManagementUser> captor = ArgumentCaptor.forClass(ManagementUser.class);
            verify(managementUserRepository).save(captor.capture());
            assertThat(captor.getValue().getPasswordHash()).isEqualTo("hashed");
            assertThat(captor.getValue().getRole()).isSameAs(role);
            assertThat(captor.getValue().getOrganization()).isSameAs(organization);
            assertThat(captor.getValue().isEnabled()).isTrue();

            assertThat(response.getId()).isEqualTo(3L);
            assertThat(response.getOrganizationId()).isEqualTo(1L);
            assertThat(response.getUsername()).isEqualTo("acme-admin");
            verify(auditService).record(eq(organization), eq("CREATE_TENANT_ADMIN"), eq("MANAGEMENT_USER"), eq("3"),
                    anyLong(), eq("Tenant administrator created"));
        }

        @Test
        void createsDisabledAdminWhenRequested() {
            Role role = new Role();
            role.setName("TENANT_ADMIN");
            when(managementUserRepository.findByOrganizationId(1L)).thenReturn(Optional.empty());
            when(managementUserRepository.existsByUsername("acme-admin")).thenReturn(false);
            when(roleRepository.findByName("TENANT_ADMIN")).thenReturn(Optional.of(role));
            when(passwordEncoder.encode("secret-123")).thenReturn("hashed");
            when(managementUserRepository.save(any(ManagementUser.class))).thenAnswer(invocation -> {
                ManagementUser saved = invocation.getArgument(0);
                saved.setId(3L);
                return saved;
            });

            TenantAdminResponse response = service.createTenantAdmin(1L, tenantAdminRequest("acme-admin", "secret-123", false));

            assertThat(response.isEnabled()).isFalse();
        }

        @Test
        void refusesSecondAdminForTheSameOrganization() {
            when(managementUserRepository.findByOrganizationId(1L)).thenReturn(Optional.of(existingAdmin()));

            assertApiException(() -> service.createTenantAdmin(1L, tenantAdminRequest("another", "secret-123", true)),
                    HttpStatus.CONFLICT, "already has a tenant administrator");

            verify(managementUserRepository, never()).save(any());
            verifyNoInteractions(auditService);
        }

        @Test
        void refusesUsernameThatIsAlreadyTaken() {
            when(managementUserRepository.findByOrganizationId(1L)).thenReturn(Optional.empty());
            when(managementUserRepository.existsByUsername("taken")).thenReturn(true);

            assertApiException(() -> service.createTenantAdmin(1L, tenantAdminRequest("taken", "secret-123", true)),
                    HttpStatus.CONFLICT, "Username");

            verify(managementUserRepository, never()).save(any());
        }

        @Test
        void failsWhenTheSeededRoleIsMissing() {
            when(managementUserRepository.findByOrganizationId(1L)).thenReturn(Optional.empty());
            when(managementUserRepository.existsByUsername("acme-admin")).thenReturn(false);
            when(roleRepository.findByName("TENANT_ADMIN")).thenReturn(Optional.empty());

            assertApiException(() -> service.createTenantAdmin(1L, tenantAdminRequest("acme-admin", "secret-123", true)),
                    HttpStatus.INTERNAL_SERVER_ERROR, "TENANT_ADMIN");

            verify(managementUserRepository, never()).save(any());
        }

        @Test
        void getReturnsAdminWithoutPasswordHash() {
            when(managementUserRepository.findByOrganizationId(1L)).thenReturn(Optional.of(existingAdmin()));

            TenantAdminResponse response = service.getTenantAdmin(1L);

            assertThat(response.getUsername()).isEqualTo("acme-admin");
            assertThat(response.getOrganizationId()).isEqualTo(1L);
        }

        @Test
        void getReportsMissingAdmin() {
            when(managementUserRepository.findByOrganizationId(1L)).thenReturn(Optional.empty());

            assertApiException(() -> service.getTenantAdmin(1L), HttpStatus.NOT_FOUND, "Tenant administrator");
        }

        @Test
        void updateRehashesPasswordAndAppliesChanges() {
            ManagementUser admin = existingAdmin();
            when(managementUserRepository.findByOrganizationId(1L)).thenReturn(Optional.of(admin));
            when(managementUserRepository.existsByUsername("renamed")).thenReturn(false);
            when(passwordEncoder.encode("new-secret-1")).thenReturn("new-hash");

            TenantAdminResponse response = service.updateTenantAdmin(1L, tenantAdminRequest("renamed", "new-secret-1", false));

            assertThat(admin.getUsername()).isEqualTo("renamed");
            assertThat(admin.getPasswordHash()).isEqualTo("new-hash");
            assertThat(admin.isEnabled()).isFalse();
            assertThat(response.isEnabled()).isFalse();
            verify(auditService).record(eq(organization), eq("UPDATE_TENANT_ADMIN"), eq("MANAGEMENT_USER"), eq("3"),
                    anyLong(), eq("Tenant administrator updated"));
        }

        @Test
        void updateAllowsKeepingTheSameUsername() {
            ManagementUser admin = existingAdmin();
            when(managementUserRepository.findByOrganizationId(1L)).thenReturn(Optional.of(admin));
            when(passwordEncoder.encode("new-secret-1")).thenReturn("new-hash");

            service.updateTenantAdmin(1L, tenantAdminRequest("acme-admin", "new-secret-1", true));

            verify(managementUserRepository, never()).existsByUsername(any());
            assertThat(admin.getPasswordHash()).isEqualTo("new-hash");
        }

        @Test
        void updateRefusesUsernameOfAnotherUser() {
            ManagementUser admin = existingAdmin();
            when(managementUserRepository.findByOrganizationId(1L)).thenReturn(Optional.of(admin));
            when(managementUserRepository.existsByUsername("platform-admin")).thenReturn(true);

            assertApiException(() -> service.updateTenantAdmin(1L, tenantAdminRequest("platform-admin", "new-secret-1", true)),
                    HttpStatus.CONFLICT);

            assertThat(admin.getUsername()).isEqualTo("acme-admin");
            assertThat(admin.getPasswordHash()).isEqualTo("old-hash");
        }

        @Test
        void deleteRemovesTheUserAfterAuditing() {
            ManagementUser admin = existingAdmin();
            when(managementUserRepository.findByOrganizationId(1L)).thenReturn(Optional.of(admin));

            service.deleteTenantAdmin(1L);

            verify(auditService).record(eq(organization), eq("DELETE_TENANT_ADMIN"), eq("MANAGEMENT_USER"), eq("3"),
                    anyLong(), eq("Tenant administrator deleted"));
            verify(managementUserRepository).delete(admin);
        }

        @Test
        void deleteReportsMissingAdmin() {
            when(managementUserRepository.findByOrganizationId(1L)).thenReturn(Optional.empty());

            assertApiException(() -> service.deleteTenantAdmin(1L), HttpStatus.NOT_FOUND);

            verify(managementUserRepository, never()).delete(any());
        }
    }

    @Nested
    class OwnOrganization {

        private Organization own;

        @BeforeEach
        void givenTenantContext() {
            own = organization(5L, "acme", OrganizationStatus.ACTIVE);
            own.setName("Acme");
            when(tenantContext.getOrganization()).thenReturn(own);
        }

        @Test
        void getReturnsTheCallersOrganization() {
            OrganizationResponse response = service.getOwnOrganization();

            assertThat(response.getId()).isEqualTo(5L);
            assertThat(response.getSlug()).isEqualTo("acme");
        }

        @Test
        void updateChangesOnlyTheName() {
            TenantOrganizationRequest request = new TenantOrganizationRequest();
            request.setName("Acme International");
            when(organizationRepository.existsByNameIgnoreCase("Acme International")).thenReturn(false);

            OrganizationResponse response = service.updateOwnOrganization(request);

            assertThat(own.getName()).isEqualTo("Acme International");
            assertThat(own.getSlug()).isEqualTo("acme");
            assertThat(own.getStatus()).isEqualTo(OrganizationStatus.ACTIVE);
            assertThat(response.getName()).isEqualTo("Acme International");
            verify(auditService).record(eq(own), eq("UPDATE_OWN_ORGANIZATION"), eq("ORGANIZATION"), eq("5"),
                    anyLong(), eq("Tenant updated its organization name"));
        }

        @Test
        void updateAllowsChangingOnlyTheLetterCaseOfItsOwnName() {
            TenantOrganizationRequest request = new TenantOrganizationRequest();
            request.setName("ACME");

            service.updateOwnOrganization(request);

            assertThat(own.getName()).isEqualTo("ACME");
            verify(organizationRepository, never()).existsByNameIgnoreCase(any());
        }

        @Test
        void updateRefusesNameOfAnotherOrganization() {
            TenantOrganizationRequest request = new TenantOrganizationRequest();
            request.setName("Globex");
            when(organizationRepository.existsByNameIgnoreCase("Globex")).thenReturn(true);

            assertApiException(() -> service.updateOwnOrganization(request), HttpStatus.CONFLICT);

            assertThat(own.getName()).isEqualTo("Acme");
            verifyNoInteractions(auditService);
        }
    }
}
