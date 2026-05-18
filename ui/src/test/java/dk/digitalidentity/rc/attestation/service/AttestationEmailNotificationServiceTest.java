package dk.digitalidentity.rc.attestation.service;

import dk.digitalidentity.rc.attestation.dao.AttestationDao;
import dk.digitalidentity.rc.attestation.dao.AttestationMailDao;
import dk.digitalidentity.rc.attestation.model.entity.Attestation;
import dk.digitalidentity.rc.dao.ItSystemDao;
import dk.digitalidentity.rc.dao.OrgUnitDao;
import dk.digitalidentity.rc.dao.UserDao;
import dk.digitalidentity.rc.dao.model.ManagerDelegate;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.service.EmailQueueService;
import dk.digitalidentity.rc.service.EmailTemplateService;
import dk.digitalidentity.rc.service.ManagerDelegateService;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.SettingsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AttestationEmailNotificationService — delegate email targeting")
class AttestationEmailNotificationServiceTest {

    @Mock private AttestationDao attestationDao;
    @Mock private AttestationEmailNotificationService self;
    @Mock private EmailTemplateService emailTemplateService;
    @Mock private EmailQueueService emailQueueService;
    @Mock private AttestationMailDao attestationMailDao;
    @Mock private SettingsService settingsService;
    @Mock private UserDao userDao;
    @Mock private OrgUnitDao orgUnitDao;
    @Mock private AttestationRunService attestationRunService;
    @Mock private ItSystemDao itSystemDao;
    @Mock private OrgUnitService orgUnitService;
    @Mock private ManagerDelegateService managerDelegateService;

    @InjectMocks
    private AttestationEmailNotificationService service;

    // ---- Helpers ---- //

    private static User makeUser(String uuid) {
        User user = new User();
        user.setUuid(uuid);
        user.setName("User " + uuid);
        return user;
    }

    private static ManagerDelegate makeManagerDelegate(User manager, User delegate) {
        return ManagerDelegate.builder()
                .manager(manager)
                .delegate(delegate)
                .fromDate(LocalDate.now())
                .indefinitely(true)
                .build();
    }

    private static Attestation delegatedAttestation(String responsibleUserUuid, String responsibleOuUuid) {
        return Attestation.builder()
                .attestationType(Attestation.AttestationType.MANAGER_DELEGATED_ATTESTATION)
                .responsibleUserUuid(responsibleUserUuid)
                .responsibleOuUuid(responsibleOuUuid)
                .createdAt(LocalDate.now())
                .deadline(LocalDate.now().plusDays(7))
                .build();
    }

    @Nested
    @DisplayName("findTargetUsers — MANAGER_DELEGATED_ATTESTATION")
    class FindTargetUsersDelegated {

        @Test
        @DisplayName("targets delegates of the responsible manager, not the manager themselves")
        void whenResponsibleUserUuidIsSet_returnsManagersDelegates() {
            // ---- Given ---- //
            User manager = makeUser("manager-uuid");
            User delegate = makeUser("delegate-uuid");
            ManagerDelegate md = makeManagerDelegate(manager, delegate);
            Attestation attestation = delegatedAttestation("manager-uuid", "ou-uuid");

            when(userDao.findByUuidAndDeletedFalse("manager-uuid")).thenReturn(Optional.of(manager));
            when(managerDelegateService.getByManager(manager)).thenReturn(List.of(md));

            // ---- When ---- //
            List<User> result = service.findTargetUsers(attestation, false);

            // ---- Then ---- //
            assertThat(result).containsExactly(delegate);
            assertThat(result).doesNotContain(manager);
        }

        @Test
        @DisplayName("resolves the manager via OrgUnit when no responsibleUserUuid is set")
        void whenResponsibleUserUuidIsNull_resolvesManagerFromOuAndReturnsDelegates() {
            // ---- Given ---- //
            User manager = makeUser("manager-uuid");
            User delegate = makeUser("delegate-uuid");
            ManagerDelegate md = makeManagerDelegate(manager, delegate);
            OrgUnit orgUnit = new OrgUnit();
            orgUnit.setManager(manager);
            Attestation attestation = delegatedAttestation(null, "ou-uuid");

            when(orgUnitDao.findById("ou-uuid")).thenReturn(Optional.of(orgUnit));
            when(managerDelegateService.getByManager(manager)).thenReturn(List.of(md));

            // ---- When ---- //
            List<User> result = service.findTargetUsers(attestation, false);

            // ---- Then ---- //
            assertThat(result).containsExactly(delegate);
            verify(orgUnitDao).findById("ou-uuid");
        }

        @Test
        @DisplayName("targets all delegates when a manager has more than one")
        void whenManagerHasMultipleDelegates_allDelegatesAreReturned() {
            // ---- Given ---- //
            User manager = makeUser("manager-uuid");
            User delegate1 = makeUser("delegate-uuid-1");
            User delegate2 = makeUser("delegate-uuid-2");
            ManagerDelegate md1 = makeManagerDelegate(manager, delegate1);
            ManagerDelegate md2 = makeManagerDelegate(manager, delegate2);
            Attestation attestation = delegatedAttestation("manager-uuid", "ou-uuid");

            when(userDao.findByUuidAndDeletedFalse("manager-uuid")).thenReturn(Optional.of(manager));
            when(managerDelegateService.getByManager(manager)).thenReturn(List.of(md1, md2));

            // ---- When ---- //
            List<User> result = service.findTargetUsers(attestation, false);

            // ---- Then ---- //
            assertThat(result).hasSize(2).containsExactlyInAnyOrder(delegate1, delegate2);
            assertThat(result).doesNotContain(manager);
        }

        @Test
        @DisplayName("returns null without querying delegates when no manager can be resolved")
        void whenNoManagerResolved_returnsNullAndDoesNotCallDelegateService() {
            // ---- Given ---- //
            // Both responsible UUIDs are null — responsibleUser stays null
            Attestation attestation = delegatedAttestation(null, null);

            // ---- When ---- //
            List<User> result = service.findTargetUsers(attestation, false);

            // ---- Then ---- //
            assertThat(result).isNull();
            verify(managerDelegateService, never()).getByManager(any());
        }
    }
}
