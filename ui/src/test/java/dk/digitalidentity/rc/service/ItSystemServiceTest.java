package dk.digitalidentity.rc.service;

import dk.digitalidentity.rc.attestation.dao.AttestationDao;
import dk.digitalidentity.rc.dao.ItSystemDao;
import dk.digitalidentity.rc.dao.history.HistoryUserDao;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static dk.digitalidentity.rc.mockfactory.attestation.MockFactory.createItSystem;
import static dk.digitalidentity.rc.mockfactory.attestation.MockFactory.createUser;
import static org.assertj.core.api.Assertions.assertThat;
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

    @InjectMocks
    private ItSystemService itSystemService;

    // ---- Helpers ---- //

    private static User makeUser(String uuid, String userId) {
        return createUser(uuid, userId, "User " + userId);
    }

    private static ItSystem makeItSystem(long id) {
        return createItSystem(id, "Test System");
    }

    // TODO: ItSystemService.updateAttestationResponsibles(long, List<User>) is currently commented
    // out in the production class as part of the multi-owner refactor. The tests below now exercise
    // the entity-level mutation helpers directly so that the refactor still has compile-time
    // coverage. Re-enable a service-level variant once the public method shape is finalised.
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
            when(itSystemDao.findById(2L)).thenReturn(Optional.of(system));

            itSystemService.updateSystemOwners(2L, List.of(user));

            assertThat(itSystemService.getSystemOwners(system)).containsExactly(user);
        }

        @Test
        @DisplayName("sets multiple owners on an empty system")
        void multipleUsers_allAdded() {
            ItSystem system = makeItSystem(2L);
            User user1 = makeUser("uuid-1", "user1");
            User user2 = makeUser("uuid-2", "user2");
            when(itSystemDao.findById(2L)).thenReturn(Optional.of(system));

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
            when(itSystemDao.findById(2L)).thenReturn(Optional.of(system));

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
            when(itSystemDao.findById(2L)).thenReturn(Optional.of(system));

            itSystemService.updateSystemOwners(2L, List.of());

            assertThat(itSystemService.getSystemOwners(system)).isEmpty();
        }
    }
}
