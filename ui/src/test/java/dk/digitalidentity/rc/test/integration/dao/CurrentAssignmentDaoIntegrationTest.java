package dk.digitalidentity.rc.test.integration.dao;

import dk.digitalidentity.rc.dao.assignment.CurrentAssignmentDao;
import dk.digitalidentity.rc.dao.assignment.HistoricAssignmentDao;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.assignment.CurrentAssignment;
import dk.digitalidentity.rc.dao.model.assignment.HistoricAssignment;
import dk.digitalidentity.rc.event.AssignmentChangeEventHandlerService;
import dk.digitalidentity.rc.service.assignment.CurrentAssignmentService;
import dk.digitalidentity.rc.test.integration.setup.BaseIntegrationTest;
import dk.digitalidentity.rc.test.integration.setup.BasicTestDataFactory;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestConstructor;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression coverage for AD-sync correctness.
 * <p>
 * Invariantet er at {@code current_assignment} ikke indeholder rækker for brugere med
 * {@code deleted = true}. Det håndhæves ved kilden i
 * {@link dk.digitalidentity.rc.service.assignment.CurrentAssignmentCalculator}: når en
 * bruger flagges slettet og køes til genberegning, returnerer calculatoren tom, og
 * {@code saveAllForUsers} sletter eksisterende rækker.
 * <p>
 * Før MR !606 (make-ad-sync-fast) brugte AdSyncApi
 * {@code userService.getUsersWithUserRole(...)} der via
 * {@code findByDeletedFalse...} udelukkede slettede brugere. Efter refactoringen kommer
 * data fra {@code current_assignment}, og uden invariantet bibeholdt slettede brugere
 * deres AD-medlemskaber.
 */
@RequiredArgsConstructor
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class CurrentAssignmentDaoIntegrationTest extends BaseIntegrationTest {

	private final BasicTestDataFactory testDataFactory;
	private final CurrentAssignmentDao currentAssignmentDao;
	private final HistoricAssignmentDao historicAssignmentDao;
	private final AssignmentChangeEventHandlerService assignmentChangeEventHandlerService;
	private final CurrentAssignmentService currentAssignmentService;

	@Test
	@DisplayName("recalculation removes current_assignment rows for users flagged deleted")
	void recalcRemovesRowsForDeletedUser() {
		BasicTestDataFactory.BasicTestData testData = testDataFactory.createBasicTestData();
		UserRole userRole = testData.urDirectlyAssigned();
		User user = testData.user();

		// Sanity: rows exist for the active user up front.
		assertThat(currentAssignmentDao.findActiveAssigned(Set.of(userRole), LocalDate.now()))
			.extracting(ca -> ca.getUser().getUuid())
			.contains(user.getUuid());

		user.setDeleted(true);
		flushAndClear();

		assignmentChangeEventHandlerService.updateUsers(Set.of(user.getUuid()));
		flushAndClear();

		assertThat(currentAssignmentDao.findActiveAssigned(Set.of(userRole), LocalDate.now()))
			.extracting(ca -> ca.getUser().getUuid())
			.doesNotContain(user.getUuid());

		assertThat(currentAssignmentDao.findByUser(user))
			.isEmpty();
	}

	@Test
	@DisplayName("AD-sync query (Collection overload) does not return deleted users after recalc")
	void adSyncQueryExcludesDeletedUserAfterRecalc() {
		BasicTestDataFactory.BasicTestData testData = testDataFactory.createBasicTestData();
		UserRole userRole = testData.urDirectlyAssigned();

		User activeUser = testData.user();
		User deletedUser = testDataFactory.createUser(
			"deleted-user-uuid", "deleted-user-id", "Deleted User", testData.itSystem().getDomain());
		testDataFactory.assignUserRoleToUser(userRole.getIdentifier(), deletedUser);
		testDataFactory.updateUserAssignmentCalculation(deletedUser);

		deletedUser.setDeleted(true);
		flushAndClear();

		// Drives the invariant: re-running recalc after flag flip clears the rows.
		assignmentChangeEventHandlerService.updateUsers(Set.of(deletedUser.getUuid()));
		flushAndClear();

		Set<CurrentAssignment> active = currentAssignmentDao.findActiveAssigned(Set.of(userRole), LocalDate.now());

		assertThat(active)
			.extracting(ca -> ca.getUser().getUuid())
			.contains(activeUser.getUuid())
			.doesNotContain(deletedUser.getUuid());
	}

	@Test
	@DisplayName("recalc closes historic_assignment rows when user is flagged deleted")
	void recalcClosesHistoricAssignmentsForDeletedUser() {
		BasicTestDataFactory.BasicTestData testData = testDataFactory.createBasicTestData();
		User user = testData.user();

		// Sanity: createBasicTestData triggered a recalc that created historic rows; they should be open.
		List<HistoricAssignment> openBefore = historicAssignmentDao.findByUserUuid(user.getUuid()).stream()
			.filter(h -> h.getValidTo() == null)
			.toList();
		assertThat(openBefore).isNotEmpty();

		user.setDeleted(true);
		flushAndClear();

		assignmentChangeEventHandlerService.updateUsers(Set.of(user.getUuid()));
		flushAndClear();

		assertThat(historicAssignmentDao.findByUserUuid(user.getUuid()))
			.allSatisfy(h -> assertThat(h.getValidTo()).as("valid_to skal sættes når user flagges deleted").isNotNull());
	}

	@Test
	@DisplayName("cleanup task processes backlog of stale rows for users that were deleted before the calculator fix")
	void cleanupTaskProcessesBacklog() {
		// Simulerer pre-fix-tilstand: bruger eksisterer som aktiv, current_assignment skrives,
		// derefter flippes deleted=true uden recalc. Det modeller den situation produktions-DB
		// vil være i når MR'en deployer — slettede brugere med stale rækker.
		BasicTestDataFactory.BasicTestData testData = testDataFactory.createBasicTestData();
		UserRole userRole = testData.urDirectlyAssigned();

		User staleDeletedUser = testDataFactory.createUser(
			"stale-deleted-uuid", "stale-deleted-id", "Stale Deleted User", testData.itSystem().getDomain());
		testDataFactory.assignUserRoleToUser(userRole.getIdentifier(), staleDeletedUser);
		testDataFactory.updateUserAssignmentCalculation(staleDeletedUser);

		staleDeletedUser.setDeleted(true);
		flushAndClear();

		// Bekræft at situationen rent faktisk er stale (rækker eksisterer for slettet bruger).
		assertThat(currentAssignmentDao.findByUser(staleDeletedUser)).isNotEmpty();

		// Replikerer cleanup-taskens logik. Tasken kalder samme service+handler-kæde,
		// men er gated af rc.scheduled.enabled (false i test).
		List<String> uuids = currentAssignmentService.findUuidsOfDeletedUsersWithCurrentAssignments(20);
		assertThat(uuids).contains(staleDeletedUser.getUuid());
		assignmentChangeEventHandlerService.updateUsers(new HashSet<>(uuids));
		flushAndClear();

		assertThat(currentAssignmentDao.findByUser(staleDeletedUser)).isEmpty();
		assertThat(historicAssignmentDao.findByUserUuid(staleDeletedUser.getUuid()))
			.allSatisfy(h -> assertThat(h.getValidTo()).isNotNull());
	}

	@Test
	@DisplayName("recalc on already-deleted user is a no-op (idempotent)")
	void recalcOnDeletedUserIsIdempotent() {
		BasicTestDataFactory.BasicTestData testData = testDataFactory.createBasicTestData();

		User deletedUser = testDataFactory.createUser(
			"already-deleted-uuid", "already-deleted-id", "Already Deleted", testData.itSystem().getDomain());
		deletedUser.setDeleted(true);
		flushAndClear();

		assignmentChangeEventHandlerService.updateUsers(Set.of(deletedUser.getUuid()));
		flushAndClear();

		assertThat(currentAssignmentDao.findByUser(deletedUser)).isEmpty();
	}
}
