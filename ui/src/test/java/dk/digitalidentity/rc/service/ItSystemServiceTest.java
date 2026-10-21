package dk.digitalidentity.rc.service;

import dk.digitalidentity.rc.attestation.dao.AttestationDao;
import dk.digitalidentity.rc.attestation.dao.AttestationResponsibleCollectionDao;
import dk.digitalidentity.rc.attestation.model.entity.AttestationResponsibleCollection;
import dk.digitalidentity.rc.dao.ItSystemDao;
import dk.digitalidentity.rc.dao.assignment.HistoricAssignmentDao;
import dk.digitalidentity.rc.dao.assignment.HistoricOuAssignmentDao;
import dk.digitalidentity.rc.dao.history.HistoryUserDao;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.KitosITSystem;
import dk.digitalidentity.rc.dao.model.KitosITSystemUser;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.enums.KitosRole;
import dk.digitalidentity.rc.service.assignment.HistoricItSystemAssignmentService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static dk.digitalidentity.rc.mockfactory.attestation.MockFactory.createItSystem;
import static dk.digitalidentity.rc.mockfactory.attestation.MockFactory.createUser;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ItSystemService — multi-owner write methods")
class ItSystemServiceTest {

    @Mock private ItSystemDao itSystemDao;
    @Mock private UserRoleService userRoleService;
    @Mock private SystemRoleService systemRoleService;
    @Mock private UserService userService;
    @Mock private OrgUnitService orgUnitService;
    @Mock private RoleGroupService roleGroupService;
    @Mock private dk.digitalidentity.rc.service.assignment.AssignmentService assignmentService;
    @Mock private EntityManager entityManager;
    @Mock private AttestationDao attestationDao;
    @Mock private HistoryUserDao historyUserDao;
    @Mock private AttestationResponsibleCollectionDao attestationResponsibleCollectionDao;
    @Mock private HistoricAssignmentDao historicAssignmentDao;
    @Mock private HistoricOuAssignmentDao historicOuAssignmentDao;
    @Mock private HistoricItSystemAssignmentService historicItSystemAssignmentService;

    @InjectMocks
    private ItSystemService itSystemService;

    // ---- Helpers ---- //

    private static User makeUser(String uuid, String userId) {
        return createUser(uuid, userId, "User " + userId);
    }

    private static ItSystem makeItSystem(long id) {
        return createItSystem(id, "Test System");
    }

    @Nested
    @DisplayName("addAttestationResponsible (entity helper)")
    class AddAttestationResponsible {

        @Test
        @DisplayName("adds a single responsible to an empty system")
        void singleUser_addedToEmptySystem() {
            ItSystem system = makeItSystem(1L);
            User user = makeUser("uuid-1", "user1");

            system.addAttestationResponsible(user);

            assertThat(itSystemService.getAttestationResponsibles(system)).containsExactly(user);
        }

        @Test
        @DisplayName("adds multiple responsibles to an empty system")
        void multipleUsers_allAdded() {
            ItSystem system = makeItSystem(1L);
            User user1 = makeUser("uuid-1", "user1");
            User user2 = makeUser("uuid-2", "user2");

            system.addAttestationResponsible(user1);
            system.addAttestationResponsible(user2);

            assertThat(itSystemService.getAttestationResponsibles(system)).containsExactlyInAnyOrder(user1, user2);
        }

        @Test
        @DisplayName("can replace existing responsibles by clearing first")
        void existingResponsiblesReplaced() {
            ItSystem system = makeItSystem(1L);
            User oldUser = makeUser("uuid-old", "old");
            system.addAttestationResponsible(oldUser);
            User newUser1 = makeUser("uuid-new-1", "new1");
            User newUser2 = makeUser("uuid-new-2", "new2");

            system.getAttestationResponsibles().clear();
            system.addAttestationResponsible(newUser1);
            system.addAttestationResponsible(newUser2);

            assertThat(itSystemService.getAttestationResponsibles(system))
                    .containsExactlyInAnyOrder(newUser1, newUser2)
                    .doesNotContain(oldUser);
        }

        @Test
        @DisplayName("clearing the responsibles collection empties it")
        void emptyList_clearsAllResponsibles() {
            ItSystem system = makeItSystem(1L);
            system.addAttestationResponsible(makeUser("uuid-1", "user1"));

            system.getAttestationResponsibles().clear();

            assertThat(itSystemService.getAttestationResponsibles(system)).isEmpty();
        }
    }

    @Nested
    @DisplayName("updateSystemOwners")
    class UpdateSystemOwners {

        @Test
        @DisplayName("sets a single owner on an empty system")
        void singleUser_addedToEmptySystem() {
            ItSystem system = makeItSystem(2L);
            User user = makeUser("uuid-1", "user1");
            when(itSystemDao.findByIdForUpdate(2L)).thenReturn(Optional.of(system));

            itSystemService.updateSystemOwners(2L, List.of(user));

            assertThat(itSystemService.getSystemOwners(system)).containsExactly(user);
        }

        @Test
        @DisplayName("sets multiple owners on an empty system")
        void multipleUsers_allAdded() {
            ItSystem system = makeItSystem(2L);
            User user1 = makeUser("uuid-1", "user1");
            User user2 = makeUser("uuid-2", "user2");
            when(itSystemDao.findByIdForUpdate(2L)).thenReturn(Optional.of(system));

            itSystemService.updateSystemOwners(2L, List.of(user1, user2));

            assertThat(itSystemService.getSystemOwners(system)).containsExactlyInAnyOrder(user1, user2);
        }

        @Test
        @DisplayName("replaces existing owners with a new list")
        void existingOwnersReplaced() {
            ItSystem system = makeItSystem(2L);
            User oldUser = makeUser("uuid-old", "old");
            system.addSystemOwner(oldUser);
            User newUser = makeUser("uuid-new", "new");
            when(itSystemDao.findByIdForUpdate(2L)).thenReturn(Optional.of(system));

            itSystemService.updateSystemOwners(2L, List.of(newUser));

            assertThat(itSystemService.getSystemOwners(system))
                    .containsExactly(newUser)
                    .doesNotContain(oldUser);
        }

        @Test
        @DisplayName("clears all owners when given an empty list")
        void emptyList_clearsAllOwners() {
            ItSystem system = makeItSystem(2L);
            system.addSystemOwner(makeUser("uuid-1", "user1"));
            when(itSystemDao.findByIdForUpdate(2L)).thenReturn(Optional.of(system));

            itSystemService.updateSystemOwners(2L, List.of());

            assertThat(itSystemService.getSystemOwners(system)).isEmpty();
        }
    }

    @Nested
    @DisplayName("reconcileAttestationResponsibleCollection")
    class ReconcileAttestationResponsibleCollection {

        @Test
        @DisplayName("returns false and does no work when collection already matches the live responsibles")
        void inSync_noWork() {
            ItSystem system = makeItSystem(1L);
            User user = makeUser("uuid-1", "user1");
            system.addAttestationResponsible(user);

            AttestationResponsibleCollection collection = AttestationResponsibleCollection.builder()
                    .id(10L).itSystemId(1L)
                    .usersUuid(new ArrayList<>(List.of("uuid-1")))
                    .build();
            when(attestationResponsibleCollectionDao.findFirstByItSystemId(1L)).thenReturn(Optional.of(collection));
            when(itSystemDao.findById(1L)).thenReturn(Optional.of(system));

            boolean reconciled = itSystemService.reconcileAttestationResponsibleCollection(1L);

            assertThat(reconciled).isFalse();
            // short-circuit: må ikke tage skrivelåsen / køre den dyre updateAttestationResponsibles-sti
            verify(itSystemDao, never()).findByIdForUpdate(1L);
            assertThat(collection.getUsersUuid()).containsExactly("uuid-1");
        }

        @Test
        @DisplayName("drops a stale responsible that the live list no longer holds")
        void partialDivergence_reconciled() {
            ItSystem system = makeItSystem(1L);
            User live = makeUser("uuid-1", "user1");
            system.addAttestationResponsible(live);

            // "uuid-2" is a former responsible left behind by the V1_352 seeding
            AttestationResponsibleCollection collection = AttestationResponsibleCollection.builder()
                    .id(10L).itSystemId(1L)
                    .usersUuid(new ArrayList<>(List.of("uuid-1", "uuid-2")))
                    .build();
            when(attestationResponsibleCollectionDao.findFirstByItSystemId(1L)).thenReturn(Optional.of(collection));
            when(itSystemDao.findById(1L)).thenReturn(Optional.of(system));

            boolean reconciled = itSystemService.reconcileAttestationResponsibleCollection(1L);

            assertThat(reconciled).isTrue();
            assertThat(collection.getUsersUuid()).containsExactly("uuid-1");
            // the live responsible list itself is untouched - only the snapshot was stale
            assertThat(itSystemService.getAttestationResponsibles(system)).containsExactly(live);
            // membership-only drift must not take the write lock, delete attestations, or run the
            // expensive historic backfills - the collection id never changed
            verify(itSystemDao, never()).findByIdForUpdate(1L);
            verify(attestationDao, never()).findByItSystemIdAndResponsibleCollectionIdAndVerifiedAtIsNull(anyLong(), anyLong());
            verify(historicAssignmentDao, never()).backfillResponsibleCollectionId(anyLong(), anyLong());
            verify(historicOuAssignmentDao, never()).backfillResponsibleCollectionId(anyLong(), anyLong());
            verify(historicItSystemAssignmentService, never()).repairResponsibleCollectionForItSystem(anyLong());
        }

        @Test
        @DisplayName("returns false when neither the collection nor the live list holds anyone")
        void bothEmpty_noWork() {
            ItSystem system = makeItSystem(1L);
            AttestationResponsibleCollection collection = AttestationResponsibleCollection.builder()
                    .id(10L).itSystemId(1L)
                    .usersUuid(new ArrayList<>())
                    .build();
            when(attestationResponsibleCollectionDao.findFirstByItSystemId(1L)).thenReturn(Optional.of(collection));
            when(itSystemDao.findById(1L)).thenReturn(Optional.of(system));

            boolean reconciled = itSystemService.reconcileAttestationResponsibleCollection(1L);

            assertThat(reconciled).isFalse();
            verify(itSystemDao, never()).findByIdForUpdate(1L);
            verify(attestationDao, never()).findByItSystemIdAndResponsibleCollectionIdAndVerifiedAtIsNull(anyLong(), anyLong());
        }

        @Test
        @DisplayName("empties a ghost collection (stale UUID, no live responsible)")
        void ghostCollection_emptied() {
            ItSystem system = makeItSystem(1L); // ingen levende systemansvarlige
            AttestationResponsibleCollection collection = AttestationResponsibleCollection.builder()
                    .id(10L).itSystemId(1L)
                    .usersUuid(new ArrayList<>(List.of("stale-uuid")))
                    .build();
            when(attestationResponsibleCollectionDao.findFirstByItSystemId(1L)).thenReturn(Optional.of(collection));
            when(itSystemDao.findById(1L)).thenReturn(Optional.of(system));
            when(itSystemDao.findByIdForUpdate(1L)).thenReturn(Optional.of(system));
            when(attestationDao.findByItSystemIdAndResponsibleCollectionIdAndVerifiedAtIsNull(1L, 10L))
                    .thenReturn(List.of());

            boolean reconciled = itSystemService.reconcileAttestationResponsibleCollection(1L);

            assertThat(reconciled).isTrue();
            assertThat(collection.getUsersUuid()).isEmpty();
        }
    }

    @Nested
    @DisplayName("handleSingleITSystemKitosOwnerAndResponsible")
    class KitosOwnerAndResponsible {

        private ItSystem kitosBackedSystem(long id, KitosITSystemUser... kitosUsers) {
            ItSystem system = makeItSystem(id);
            KitosITSystem kitosITSystem = new KitosITSystem();
            kitosITSystem.setKitosUuid(UUID.randomUUID());
            kitosITSystem.setName("Kitos System");
            for (KitosITSystemUser kitosUser : kitosUsers) {
                kitosUser.setKitosITSystem(kitosITSystem);
                kitosITSystem.getKitosUsers().add(kitosUser);
            }
            system.setKitosITSystem(kitosITSystem);
            return system;
        }

        private KitosITSystemUser kitosUser(KitosRole role, String name, String email) {
            KitosITSystemUser kitosUser = new KitosITSystemUser();
            kitosUser.setKitosUuid(UUID.randomUUID());
            kitosUser.setRole(role);
            kitosUser.setName(name);
            kitosUser.setEmail(email);
            return kitosUser;
        }

        private void stubWritePaths(long id, ItSystem system) {
            lenient().when(itSystemDao.findByIdForUpdate(id)).thenReturn(Optional.of(system));
            lenient().when(attestationResponsibleCollectionDao.findFirstByItSystemId(id))
                    .thenReturn(Optional.of(AttestationResponsibleCollection.builder()
                            .id(90L).itSystemId(id).usersUuid(new ArrayList<>()).build()));
            lenient().when(attestationResponsibleCollectionDao.save(any())).thenAnswer(i -> i.getArgument(0));
        }

        @Test
        @DisplayName("maps every Kitos responsible, not just the first")
        void multipleResponsibles_allMapped() {
            ItSystem system = kitosBackedSystem(3L,
                    kitosUser(KitosRole.SYSTEM_RESPONSIBLE, "First Responsible", "first@example.com"),
                    kitosUser(KitosRole.SYSTEM_RESPONSIBLE, "Second Responsible", "second@example.com"));
            User first = createUser("uuid-1", "first", "First Responsible", "first@example.com");
            User second = createUser("uuid-2", "second", "Second Responsible", "second@example.com");
            stubWritePaths(3L, system);

            itSystemService.handleSingleITSystemKitosOwnerAndResponsible(system, List.of(first, second));

            assertThat(itSystemService.getAttestationResponsibles(system)).containsExactlyInAnyOrder(first, second);
        }

        @Test
        @DisplayName("maps every Kitos system owner, not just the first")
        void multipleOwners_allMapped() {
            ItSystem system = kitosBackedSystem(4L,
                    kitosUser(KitosRole.SYSTEM_OWNER, "First Owner", "first@example.com"),
                    kitosUser(KitosRole.SYSTEM_OWNER, "Second Owner", "second@example.com"));
            User first = createUser("uuid-1", "first", "First Owner", "first@example.com");
            User second = createUser("uuid-2", "second", "Second Owner", "second@example.com");
            stubWritePaths(4L, system);

            itSystemService.handleSingleITSystemKitosOwnerAndResponsible(system, List.of(first, second));

            assertThat(itSystemService.getSystemOwners(system)).containsExactlyInAnyOrder(first, second);
        }

        @Test
        @DisplayName("keeps the responsibles it can match when an earlier Kitos user matches nobody")
        void unmatchableKitosUser_doesNotBlockTheRest() {
            ItSystem system = kitosBackedSystem(5L,
                    kitosUser(KitosRole.SYSTEM_RESPONSIBLE, "Unknown Person", "unknown@example.com"),
                    kitosUser(KitosRole.SYSTEM_RESPONSIBLE, "Known Person", "known@example.com"));
            User known = createUser("uuid-1", "known", "Known Person", "known@example.com");
            stubWritePaths(5L, system);

            itSystemService.handleSingleITSystemKitosOwnerAndResponsible(system, List.of(known));

            assertThat(itSystemService.getAttestationResponsibles(system)).containsExactly(known);
        }

        @Test
        @DisplayName("falls back to name when the Kitos email differs from the catalogue email")
        void emailMismatch_matchedByName() {
            ItSystem system = kitosBackedSystem(6L,
                    kitosUser(KitosRole.SYSTEM_RESPONSIBLE, "Named Person", "named@kitos.example.com"));
            User named = createUser("uuid-1", "named", "Named Person", "named@catalogue.example.com");
            stubWritePaths(6L, system);

            itSystemService.handleSingleITSystemKitosOwnerAndResponsible(system, List.of(named));

            assertThat(itSystemService.getAttestationResponsibles(system)).containsExactly(named);
        }

        @Test
        @DisplayName("skips an ambiguous name rather than picking one of the namesakes")
        void ambiguousName_skipped() {
            ItSystem system = kitosBackedSystem(7L,
                    kitosUser(KitosRole.SYSTEM_RESPONSIBLE, "Shared Name", "no-such-mail@example.com"),
                    kitosUser(KitosRole.SYSTEM_RESPONSIBLE, "Shared Name", "exact@example.com"));
            User byEmail = createUser("uuid-1", "byemail", "Shared Name", "exact@example.com");
            User namesake = createUser("uuid-2", "namesake", "Shared Name", "other@example.com");
            stubWritePaths(7L, system);

            itSystemService.handleSingleITSystemKitosOwnerAndResponsible(system, List.of(byEmail, namesake));

            // navnet peger på to brugere, og navnefællen er ikke nævnt i Kitos - vi må ikke gætte
            assertThat(itSystemService.getAttestationResponsibles(system)).containsExactly(byEmail);
        }

        @Test
        @DisplayName("two Kitos users resolving to the same catalogue user yield one responsible")
        void sameCatalogueUserTwice_deduplicated() {
            ItSystem system = kitosBackedSystem(10L,
                    kitosUser(KitosRole.SYSTEM_RESPONSIBLE, "Shared Name", "shared@example.com"),
                    kitosUser(KitosRole.SYSTEM_RESPONSIBLE, "Shared Name", "shared@example.com"));
            User shared = createUser("uuid-1", "shared", "Shared Name", "shared@example.com");
            User namesake = createUser("uuid-2", "namesake", "Shared Name", "other@example.com");
            stubWritePaths(10L, system);

            itSystemService.handleSingleITSystemKitosOwnerAndResponsible(system, List.of(shared, namesake));

            assertThat(itSystemService.getAttestationResponsibles(system)).containsExactly(shared);
        }

        @Test
        @DisplayName("skips a Kitos user with no email instead of matching a blank catalogue email")
        void blankEmail_notMatched() {
            ItSystem system = kitosBackedSystem(11L,
                    kitosUser(KitosRole.SYSTEM_RESPONSIBLE, "Unknown Person", null));
            User blankEmail = createUser("uuid-1", "blank", "Someone Else", "");
            stubWritePaths(11L, system);

            itSystemService.handleSingleITSystemKitosOwnerAndResponsible(system, List.of(blankEmail));

            assertThat(itSystemService.getAttestationResponsibles(system)).isEmpty();
        }

        @Test
        @DisplayName("does not touch the collections when Kitos already agrees with the current state")
        void alreadyInSync_noWrite() {
            User first = createUser("uuid-1", "first", "First Responsible", "first@example.com");
            User second = createUser("uuid-2", "second", "Second Responsible", "second@example.com");
            ItSystem system = kitosBackedSystem(9L,
                    kitosUser(KitosRole.SYSTEM_RESPONSIBLE, first.getName(), first.getEmail()),
                    kitosUser(KitosRole.SYSTEM_RESPONSIBLE, second.getName(), second.getEmail()));
            system.addAttestationResponsible(first);
            system.addAttestationResponsible(second);

            itSystemService.handleSingleITSystemKitosOwnerAndResponsible(system, List.of(first, second));

            verify(itSystemDao, never()).findByIdForUpdate(9L);
            verify(entityManager, never()).refresh(any(), any(jakarta.persistence.LockModeType.class));
        }
    }
}
