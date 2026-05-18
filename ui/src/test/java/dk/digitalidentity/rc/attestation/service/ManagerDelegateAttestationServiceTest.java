package dk.digitalidentity.rc.attestation.service;

import dk.digitalidentity.rc.attestation.dao.AttestationDao;
import dk.digitalidentity.rc.attestation.dao.AttestationOuAssignmentsDao;
import dk.digitalidentity.rc.attestation.dao.AttestationUserRoleAssignmentDao;
import dk.digitalidentity.rc.attestation.model.entity.Attestation;
import dk.digitalidentity.rc.attestation.model.entity.AttestationRun;
import dk.digitalidentity.rc.dao.ManagerDelegateDao;
import dk.digitalidentity.rc.dao.OrgUnitDao;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.projections.OrgUnitManagerUuid;
import dk.digitalidentity.rc.service.ManagerDelegateService;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.UserService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ManagerDelegateAttestationService — delegate attestation filtering")
class ManagerDelegateAttestationServiceTest {

    @Mock private EntityManager entityManager;
    @Mock private ManagerDelegateDao managerDelegateDao;
    @Mock private OrganisationAttestationService organisationAttestationService;
    @Mock private AttestationUserRoleAssignmentDao userRoleAssignmentDao;
    @Mock private AttestationOuAssignmentsDao ouAssignmentsDao;
    @Mock private AttestationDao attestationDao;
    @Mock private OrgUnitDao orgUnitDao;
    @Mock private OrgUnitService orgUnitService;
    @Mock private UserService userService;
    @Mock private ManagerDelegateService managerDelegateService;

    @InjectMocks
    private ManagerDelegateAttestationService service;

    // ---- Helpers ---- //

    private static User makeUser(String uuid, String name) {
        User user = new User();
        user.setUuid(uuid);
        user.setName(name);
        return user;
    }

    private static Attestation delegatedAttestation(String ouUuid) {
        return Attestation.builder()
                .attestationType(Attestation.AttestationType.MANAGER_DELEGATED_ATTESTATION)
                .responsibleOuUuid(ouUuid)
                .responsibleOuName("OU " + ouUuid)
                .createdAt(LocalDate.now())
                .deadline(LocalDate.now().plusDays(7))
                .build();
    }

    private static Attestation organisationAttestation(String ouUuid) {
        return Attestation.builder()
                .attestationType(Attestation.AttestationType.ORGANISATION_ATTESTATION)
                .responsibleOuUuid(ouUuid)
                .createdAt(LocalDate.now())
                .deadline(LocalDate.now().plusDays(7))
                .build();
    }

    private static OrgUnitManagerUuid projection(String ouUuid, String managerUuid) {
        return new OrgUnitManagerUuid() {
            @Override public String getUuid() { return ouUuid; }
            @Override public ManagerUuid getManager() { return () -> managerUuid; }
        };
    }

    private void stubToShallowOrganisationDtoHelpers() {
        when(userRoleAssignmentDao.listValidAssignmentsByResponsibleOu(any(), any())).thenReturn(Collections.emptyList());
        when(ouAssignmentsDao.listValidNotInheritedAssignmentsForOu(any(), any())).thenReturn(Collections.emptyList());
        when(organisationAttestationService.orgUnitRoleGroups(any())).thenReturn(Collections.emptyList());
        when(organisationAttestationService.orgUnitUserRolesPrItSystem(any(), any())).thenReturn(Collections.emptyList());
        when(organisationAttestationService.buildUserAttestations(any(), any(), any(boolean.class), any())).thenReturn(Collections.emptyList());
        when(orgUnitService.getManagerName(any())).thenReturn(java.util.Optional.empty());
    }

    @Nested
    @DisplayName("listOrganisationsForAttestation")
    class ListOrganisationsForAttestation {

        @Test
        @DisplayName("includes only attestations managed by one of the delegated managers")
        void includesAttestationWhoseOuManagerIsDelegated() {
            // ---- Given ---- //
            User managerM = makeUser("manager-m-uuid", "Manager M");
            Attestation attM = delegatedAttestation("ou-m");
            Attestation attN = delegatedAttestation("ou-n");
            AttestationRun run = AttestationRun.builder().attestations(List.of(attM, attN)).build();

            OrgUnitManagerUuid projM = projection("ou-m", "manager-m-uuid");
            OrgUnitManagerUuid projN = projection("ou-n", "manager-n-uuid");
            when(orgUnitDao.findByActiveTrueAndManagerNotNullAndUuidIn(any()))
                    .thenReturn(List.of(projM, projN));
            stubToShallowOrganisationDtoHelpers();

            // ---- When ---- //
            // delegatedManagers contains only managerM — attN should be excluded
            var result = service.listOrganisationsForAttestation(run, List.of(managerM));

            // ---- Then ---- //
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("excludes attestations whose OU manager is not among the delegated managers")
        void excludesAttestationWhoseOuManagerIsNotDelegated() {
            // ---- Given ---- //
            User managerM = makeUser("manager-m-uuid", "Manager M");
            Attestation attM = delegatedAttestation("ou-m");
            Attestation attN = delegatedAttestation("ou-n");
            AttestationRun run = AttestationRun.builder().attestations(List.of(attM, attN)).build();

            OrgUnitManagerUuid projM = projection("ou-m", "manager-m-uuid");
            OrgUnitManagerUuid projN = projection("ou-n", "manager-n-uuid");
            when(orgUnitDao.findByActiveTrueAndManagerNotNullAndUuidIn(any()))
                    .thenReturn(List.of(projM, projN));
            stubToShallowOrganisationDtoHelpers();

            // ---- When ---- //
            var result = service.listOrganisationsForAttestation(run, List.of(managerM));

            // ---- Then ---- //
            // managerN is not in delegatedManagers — attN must be excluded
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("ignores non-delegated attestation types and does not include their OUs in the DAO query")
        void excludesNonDelegatedAttestationType() {
            // ---- Given ---- //
            User managerM = makeUser("manager-m-uuid", "Manager M");
            Attestation attM = delegatedAttestation("ou-m");
            Attestation attOrg = organisationAttestation("ou-org");
            AttestationRun run = AttestationRun.builder().attestations(List.of(attM, attOrg)).build();

            OrgUnitManagerUuid projM = projection("ou-m", "manager-m-uuid");
            when(orgUnitDao.findByActiveTrueAndManagerNotNullAndUuidIn(any()))
                    .thenReturn(List.of(projM));
            stubToShallowOrganisationDtoHelpers();

            // ---- When ---- //
            var result = service.listOrganisationsForAttestation(run, List.of(managerM));

            // ---- Then ---- //
            assertThat(result).hasSize(1);

            // The DAO must only be called with the MANAGER_DELEGATED ouUuid, not the ORGANISATION_ATTESTATION one
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<String>> uuidCaptor = ArgumentCaptor.forClass(List.class);
            verify(orgUnitDao).findByActiveTrueAndManagerNotNullAndUuidIn(uuidCaptor.capture());
            assertThat(uuidCaptor.getValue()).containsExactly("ou-m");
            assertThat(uuidCaptor.getValue()).doesNotContain("ou-org");
        }

        @Test
        @DisplayName("returns empty list when the OU has no manager (inactive or manager unset)")
        void excludesAttestationWhenOuHasNoManagerInProjection() {
            // ---- Given ---- //
            User managerM = makeUser("manager-m-uuid", "Manager M");
            Attestation attM = delegatedAttestation("ou-m");
            AttestationRun run = AttestationRun.builder().attestations(List.of(attM)).build();

            // DAO returns nothing — ou-m is not active or has no manager
            when(orgUnitDao.findByActiveTrueAndManagerNotNullAndUuidIn(any())).thenReturn(Collections.emptyList());

            // ---- When ---- //
            var result = service.listOrganisationsForAttestation(run, List.of(managerM));

            // ---- Then ---- //
            assertThat(result).isEmpty();
        }
    }
}
