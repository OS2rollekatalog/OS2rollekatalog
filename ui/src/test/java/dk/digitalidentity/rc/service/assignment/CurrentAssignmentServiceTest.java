package dk.digitalidentity.rc.service.assignment;

import dk.digitalidentity.rc.dao.assignment.CurrentAssignmentDao;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.assignment.CurrentAssignment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createCurrentAssignment;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createUser;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class CurrentAssignmentServiceTest {

	@Mock
	private CurrentAssignmentDao currentAssignmentDao;
	@Mock
	private HistoricAssignmentService historicAssignmentService;

	@InjectMocks
	private CurrentAssignmentService currentAssignmentService;

	// ---- Common setup ---- //
	private User testUser;

	@BeforeEach
	void setup() {
		testUser = new User();
		testUser.setUuid("user-uuid-123");
	}

	// ---- ------------- ---- //

	@Nested
	class saveAllForUsersTests {

		@Test
		@DisplayName("should delete existing assignments not matching any of the new ones")
		void shouldDeleteExistingNotMatchingProvided() {
			// ---- Given ---- //
			CurrentAssignment existingNonMatching = createCurrentAssignment(1L, "outdated-hash", testUser);
			CurrentAssignment existingMatching = createCurrentAssignment(2L, "existing-hash", testUser);
			CurrentAssignment newAssignmentNonExisting = createCurrentAssignment(null, "new-hash", testUser);
			CurrentAssignment newAssignmentExisting = createCurrentAssignment(null, "existing-hash", testUser);

			given(currentAssignmentDao.findByUserIn(Set.of(testUser)))
				.willReturn(new HashSet<>(Arrays.asList(existingNonMatching, existingMatching)));

			// ---- When ---- //
			currentAssignmentService.saveAllForUsers(Map.of(testUser, Set.of(newAssignmentNonExisting, newAssignmentExisting)));

			// ---- Then ---- //
			ArgumentCaptor<List<Long>> deleteCaptor = ArgumentCaptor.forClass(List.class);

			// verify method is called
			verify(currentAssignmentDao).deleteAllById(deleteCaptor.capture());

			// verify that only the old assignment not matching a new one is passed as argument
			assertThat(deleteCaptor.getValue())
				.containsExactly(1L);
		}

		@Test
		@DisplayName("should insert new ones that do not exist")
		void shouldInsertNonExisting() {
			// ---- Given ---- //
			CurrentAssignment existingNonMatching = createCurrentAssignment(1L, "outdated-hash", testUser);
			CurrentAssignment existingMatching = createCurrentAssignment(2L, "existing-hash", testUser);
			CurrentAssignment newAssignmentNonExisting = createCurrentAssignment(null, "new-hash", testUser);
			CurrentAssignment newAssignmentExisting = createCurrentAssignment(null, "existing-hash", testUser);

			given(currentAssignmentDao.findByUserIn(Set.of(testUser)))
				.willReturn(new HashSet<>(Arrays.asList(existingNonMatching, existingMatching)));

			// ---- When ---- //
			currentAssignmentService.saveAllForUsers(Map.of(testUser, Set.of(newAssignmentNonExisting, newAssignmentExisting)));

			// ---- Then ---- //
			ArgumentCaptor<Set<CurrentAssignment>> saveCaptor = ArgumentCaptor.forClass(Set.class);

			// verify method is called
			verify(currentAssignmentDao).saveAll(saveCaptor.capture());

			// verify that the new assignment is saved
			Set<String> savedHashes = saveCaptor.getValue().stream()
				.map(CurrentAssignment::getRecordHash)
				.collect(Collectors.toSet());

			// verify with a set recordhash, as a new one is first generated after being saved to DB
			assertThat(savedHashes)
				.contains("new-hash")
				.doesNotContain("outdated-hash", "existing-hash");
		}

		@Test
		@DisplayName("should update historic assignments for deleted current assignments")
		void shouldUpdateHistoricAssignmentForDeleted() {
			// ---- Given ---- //
			CurrentAssignment existingNonMatching = createCurrentAssignment(1L, "outdated-hash", testUser);
			CurrentAssignment existingMatching = createCurrentAssignment(2L, "existing-hash", testUser);
			CurrentAssignment newAssignmentNonExisting = createCurrentAssignment(null, "new-hash", testUser);
			CurrentAssignment newAssignmentExisting = createCurrentAssignment(null, "existing-hash", testUser);

			given(currentAssignmentDao.findByUserIn(Set.of(testUser)))
				.willReturn(Set.of(existingNonMatching, existingMatching));

			// ---- When ---- //
			currentAssignmentService.saveAllForUsers(Map.of(testUser, Set.of(newAssignmentNonExisting, newAssignmentExisting)));

			// ---- Then ---- //
			ArgumentCaptor<Set<CurrentAssignment>> deletedCaptor = ArgumentCaptor.forClass(Set.class);

			// verify method is called
			verify(historicAssignmentService).updateValidToFor(deletedCaptor.capture(), any(LocalDateTime.class));

			// verify that the new assignment is saved
			Set<String> updatedHistoricHashes = deletedCaptor.getValue().stream()
				.map(CurrentAssignment::getRecordHash)
				.collect(Collectors.toSet());

			// verify with the generated hash, since the update operation generates a new one
			assertThat(updatedHistoricHashes)
				.contains("outdated-hash")
				.doesNotContain("new-hash", "existing-hash");
		}

		@Test
		@DisplayName("should create historic assignments for new current assignments")
		void shouldCreateHistoricAssignmentsForNew() {
			// ---- Given ---- //
			CurrentAssignment existingNonMatching = createCurrentAssignment(1L, "outdated-hash", testUser);
			CurrentAssignment existingMatching = createCurrentAssignment(2L, "existing-hash", testUser);
			CurrentAssignment newAssignmentNonExisting = createCurrentAssignment(null, "new-hash", testUser);
			CurrentAssignment newAssignmentExisting = createCurrentAssignment(null, "existing-hash", testUser);

			given(currentAssignmentDao.findByUserIn(Set.of(testUser)))
				.willReturn(Set.of(existingNonMatching, existingMatching));

			// ---- When ---- //
			currentAssignmentService.saveAllForUsers(Map.of(testUser, Set.of(newAssignmentNonExisting, newAssignmentExisting)));

			// ---- Then ---- //
			ArgumentCaptor<Set<CurrentAssignment>> createdCaptor = ArgumentCaptor.forClass(Set.class);

			// verify method is called
			verify(historicAssignmentService).createFromCurrentAssignments(createdCaptor.capture());

			// verify that the new assignment is saved
			Set<String> createdHistoricHashes = createdCaptor.getValue().stream()
				.map(CurrentAssignment::getRecordHash)
				.collect(Collectors.toSet());

			// verify with the generated hash, since the update operation generates a new one
			assertThat(createdHistoricHashes)
				.contains("new-hash")
				.doesNotContain("outdated-hash", "existing-hash");
		}

		@Test
		@DisplayName("should not touch the DB when input map is empty")
		void shouldShortCircuitOnEmptyInput() {
			// ---- When ---- //
			currentAssignmentService.saveAllForUsers(Map.of());

			// ---- Then ---- //
			verifyNoInteractions(currentAssignmentDao, historicAssignmentService);
		}

		@Test
		@DisplayName("should isolate deletes and inserts per user when processing multiple users")
		void shouldIsolateChangesPerUser() {
			// ---- Given ---- //
			User userA = createUser("user-a");
			User userB = createUser("user-b");

			CurrentAssignment existingA = createCurrentAssignment(1L, "hash-a-old", userA);
			CurrentAssignment existingB = createCurrentAssignment(2L, "hash-b-old", userB);
			CurrentAssignment newA = createCurrentAssignment(null, "hash-a-new", userA);
			CurrentAssignment newB = createCurrentAssignment(null, "hash-b-old", userB); // unchanged

			given(currentAssignmentDao.findByUserIn(Set.of(userA, userB)))
				.willReturn(Set.of(existingA, existingB));

			// ---- When ---- //
			currentAssignmentService.saveAllForUsers(Map.of(userA, Set.of(newA), userB, Set.of(newB)));

			// ---- Then ---- //
			ArgumentCaptor<List<Long>> deleteCaptor = ArgumentCaptor.forClass(List.class);
			verify(currentAssignmentDao).deleteAllById(deleteCaptor.capture());

			// only userA's old assignment should be deleted, not userB's unchanged one
			assertThat(deleteCaptor.getValue()).containsExactly(1L);

			ArgumentCaptor<Set<CurrentAssignment>> saveCaptor = ArgumentCaptor.forClass(Set.class);
			verify(currentAssignmentDao).saveAll(saveCaptor.capture());

			// only userA's new assignment should be created
			assertThat(saveCaptor.getValue().stream().map(CurrentAssignment::getRecordHash).collect(Collectors.toSet()))
				.containsExactly("hash-a-new");
		}
	}
}
