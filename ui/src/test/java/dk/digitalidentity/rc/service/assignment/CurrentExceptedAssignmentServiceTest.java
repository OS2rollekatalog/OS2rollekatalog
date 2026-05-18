package dk.digitalidentity.rc.service.assignment;

import dk.digitalidentity.rc.dao.assignment.CurrentExceptedAssignmentDao;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.assignment.CurrentExceptedAssignment;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createCurrentExceptedAssignment;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class CurrentExceptedAssignmentServiceTest {

	@Mock
	private CurrentExceptedAssignmentDao currentExceptedAssignmentDao;
	@Mock
	private HistoricExceptedAssignmentService historicExceptedAssignmentService;

	@InjectMocks
	private CurrentExceptedAssignmentService currentExceptedAssignmentService;

	private User testUser;

	@BeforeEach
	void setup() {
		testUser = new User();
		testUser.setUuid("user-uuid-123");
	}

	@SuppressWarnings("unchecked")
	private ArgumentCaptor<Set<CurrentExceptedAssignment>> assignmentSetCaptor() {
		return ArgumentCaptor.forClass(Set.class);
	}

	private Set<String> extractHashes(Set<CurrentExceptedAssignment> assignments) {
		return assignments.stream()
			.map(CurrentExceptedAssignment::getRecordHash)
			.collect(Collectors.toSet());
	}

	@Nested
	@DisplayName("saveAllForUsers")
	class SaveAllForUsersTests {

		private CurrentExceptedAssignment existingNonMatching;
		private CurrentExceptedAssignment existingMatching;
		private CurrentExceptedAssignment newAssignmentNonExisting;
		private CurrentExceptedAssignment newAssignmentExisting;

		@BeforeEach
		void setupAssignments() {
			existingNonMatching = createCurrentExceptedAssignment(1L, "outdated-hash", testUser.getUuid());
			existingMatching = createCurrentExceptedAssignment(2L, "existing-hash", testUser.getUuid());
			newAssignmentNonExisting = createCurrentExceptedAssignment(null, "new-hash", testUser.getUuid());
			newAssignmentExisting = createCurrentExceptedAssignment(null, "existing-hash", testUser.getUuid());
		}

		@Test
		@DisplayName("should delete existing excepted assignments not matching any of the new ones")
		void shouldDeleteExistingNotMatchingProvided() {
			// Arrange
			given(currentExceptedAssignmentDao.findAllByExceptionUserUuidIn(Set.of(testUser.getUuid())))
				.willReturn(new HashSet<>(Set.of(existingNonMatching, existingMatching)));

			// Act
			currentExceptedAssignmentService.saveAllForUsers(Map.of(testUser, Set.of(newAssignmentNonExisting, newAssignmentExisting)));

			// Assert
			ArgumentCaptor<Set<CurrentExceptedAssignment>> deleteCaptor = assignmentSetCaptor();
			verify(currentExceptedAssignmentDao).deleteAll(deleteCaptor.capture());

			assertThat(extractHashes(deleteCaptor.getValue()))
				.containsExactly("outdated-hash");
		}

		@Test
		@DisplayName("should insert new excepted assignments that do not already exist")
		void shouldInsertNonExisting() {
			// Arrange
			given(currentExceptedAssignmentDao.findAllByExceptionUserUuidIn(Set.of(testUser.getUuid())))
				.willReturn(new HashSet<>(Set.of(existingNonMatching, existingMatching)));

			// Act
			currentExceptedAssignmentService.saveAllForUsers(Map.of(testUser, Set.of(newAssignmentNonExisting, newAssignmentExisting)));

			// Assert
			ArgumentCaptor<Set<CurrentExceptedAssignment>> saveCaptor = assignmentSetCaptor();
			verify(currentExceptedAssignmentDao).saveAll(saveCaptor.capture());

			assertThat(extractHashes(saveCaptor.getValue()))
				.containsExactly("new-hash");
		}

		@Test
		@DisplayName("should update historic excepted assignments validTo for deleted current assignments")
		void shouldUpdateHistoricValidToForDeleted() {
			// Arrange
			given(currentExceptedAssignmentDao.findAllByExceptionUserUuidIn(Set.of(testUser.getUuid())))
				.willReturn(new HashSet<>(Set.of(existingNonMatching, existingMatching)));

			// Act
			currentExceptedAssignmentService.saveAllForUsers(Map.of(testUser, Set.of(newAssignmentExisting)));

			// Assert
			ArgumentCaptor<Set<CurrentExceptedAssignment>> deletedCaptor = assignmentSetCaptor();
			verify(historicExceptedAssignmentService).updateValidToFor(deletedCaptor.capture(), any(LocalDateTime.class));

			assertThat(extractHashes(deletedCaptor.getValue()))
				.containsExactly("outdated-hash");
		}

		@Test
		@DisplayName("should create historic excepted assignments for newly created current assignments")
		void shouldCreateHistoricForNewAssignments() {
			// Arrange
			given(currentExceptedAssignmentDao.findAllByExceptionUserUuidIn(Set.of(testUser.getUuid())))
				.willReturn(new HashSet<>(Set.of(existingMatching)));

			// Act
			currentExceptedAssignmentService.saveAllForUsers(Map.of(testUser, Set.of(newAssignmentNonExisting, newAssignmentExisting)));

			// Assert
			ArgumentCaptor<Set<CurrentExceptedAssignment>> createdCaptor = assignmentSetCaptor();
			verify(historicExceptedAssignmentService).createExceptedFromCurrentAssignments(createdCaptor.capture());

			assertThat(extractHashes(createdCaptor.getValue()))
				.containsExactly("new-hash");
		}

		@Test
		@DisplayName("should delete all existing when input set is empty")
		void shouldDeleteAllWhenInputIsEmpty() {
			// Arrange
			CurrentExceptedAssignment existing1 = createCurrentExceptedAssignment(1L, "hash-1", testUser.getUuid());
			CurrentExceptedAssignment existing2 = createCurrentExceptedAssignment(2L, "hash-2", testUser.getUuid());

			given(currentExceptedAssignmentDao.findAllByExceptionUserUuidIn(Set.of(testUser.getUuid())))
				.willReturn(new HashSet<>(Set.of(existing1, existing2)));

			// Act
			currentExceptedAssignmentService.saveAllForUsers(Map.of(testUser, Set.of()));

			// Assert
			ArgumentCaptor<Set<CurrentExceptedAssignment>> deleteCaptor = assignmentSetCaptor();
			verify(currentExceptedAssignmentDao).deleteAll(deleteCaptor.capture());

			assertThat(extractHashes(deleteCaptor.getValue()))
				.containsExactlyInAnyOrder("hash-1", "hash-2");
		}

		@Test
		@DisplayName("should insert all when no existing assignments")
		void shouldInsertAllWhenNoExisting() {
			// Arrange
			CurrentExceptedAssignment newAssignment1 = createCurrentExceptedAssignment(null, "new-hash-1", testUser.getUuid());
			CurrentExceptedAssignment newAssignment2 = createCurrentExceptedAssignment(null, "new-hash-2", testUser.getUuid());

			given(currentExceptedAssignmentDao.findAllByExceptionUserUuidIn(Set.of(testUser.getUuid())))
				.willReturn(new HashSet<>());

			// Act
			currentExceptedAssignmentService.saveAllForUsers(Map.of(testUser, Set.of(newAssignment1, newAssignment2)));

			// Assert
			ArgumentCaptor<Set<CurrentExceptedAssignment>> saveCaptor = assignmentSetCaptor();
			verify(currentExceptedAssignmentDao).saveAll(saveCaptor.capture());

			assertThat(extractHashes(saveCaptor.getValue()))
				.containsExactlyInAnyOrder("new-hash-1", "new-hash-2");
		}

		@Test
		@DisplayName("should return true when new assignments were created")
		void shouldReturnTrueWhenAssignmentsCreated() {
			// Arrange
			CurrentExceptedAssignment newAssignment = createCurrentExceptedAssignment(null, "new-hash", testUser.getUuid());
			CurrentExceptedAssignment savedAssignment = createCurrentExceptedAssignment(1L, "new-hash", testUser.getUuid());

			given(currentExceptedAssignmentDao.findAllByExceptionUserUuid(testUser.getUuid()))
				.willReturn(new HashSet<>());
			given(currentExceptedAssignmentDao.saveAll(any()))
				.willReturn(List.of(savedAssignment));

			// Act
			boolean result = currentExceptedAssignmentService.saveAllForUser(testUser, Set.of(newAssignment));

			// Assert
			assertThat(result).isTrue();
		}

		@Test
		@DisplayName("should not touch the DB when input map is empty")
		void shouldShortCircuitOnEmptyInput() {
			// Act
			currentExceptedAssignmentService.saveAllForUsers(Map.of());

			// Assert
			verifyNoInteractions(currentExceptedAssignmentDao, historicExceptedAssignmentService);
		}

		@Test
		@DisplayName("should isolate changes per user when processing multiple users")
		void shouldIsolateChangesPerUser() {
			// Arrange
			User userA = new User();
			userA.setUuid("user-a");
			User userB = new User();
			userB.setUuid("user-b");

			CurrentExceptedAssignment existingA = createCurrentExceptedAssignment(1L, "hash-a-old", userA.getUuid());
			CurrentExceptedAssignment existingB = createCurrentExceptedAssignment(2L, "hash-b-keep", userB.getUuid());
			CurrentExceptedAssignment newA = createCurrentExceptedAssignment(null, "hash-a-new", userA.getUuid());
			CurrentExceptedAssignment newB = createCurrentExceptedAssignment(null, "hash-b-keep", userB.getUuid()); // unchanged

			given(currentExceptedAssignmentDao.findAllByExceptionUserUuidIn(Set.of(userA.getUuid(), userB.getUuid())))
				.willReturn(new HashSet<>(Set.of(existingA, existingB)));

			// Act
			currentExceptedAssignmentService.saveAllForUsers(Map.of(userA, Set.of(newA), userB, Set.of(newB)));

			// Assert
			ArgumentCaptor<Set<CurrentExceptedAssignment>> deleteCaptor = assignmentSetCaptor();
			verify(currentExceptedAssignmentDao).deleteAll(deleteCaptor.capture());

			// only userA's old assignment should be deleted
			assertThat(extractHashes(deleteCaptor.getValue())).containsExactly("hash-a-old");

			ArgumentCaptor<Set<CurrentExceptedAssignment>> saveCaptor = assignmentSetCaptor();
			verify(currentExceptedAssignmentDao).saveAll(saveCaptor.capture());

			// only userA's new assignment should be created
			assertThat(extractHashes(saveCaptor.getValue())).containsExactly("hash-a-new");
		}
	}
}
