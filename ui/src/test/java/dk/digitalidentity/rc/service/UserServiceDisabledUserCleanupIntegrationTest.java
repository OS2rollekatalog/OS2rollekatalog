package dk.digitalidentity.rc.service;

import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.assignment.CurrentAssignment;
import dk.digitalidentity.rc.interceptor.RoleChangeInterceptor;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.assignment.AssignmentService;
import dk.digitalidentity.rc.test.integration.setup.BaseIntegrationTest;
import dk.digitalidentity.rc.test.integration.setup.BasicTestDataFactory;
import dk.digitalidentity.rc.test.integration.setup.BasicTestDataFactory.BasicTestData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Regression coverage for the cleanup of direct assignments on disabled users, which used to clear the assignment
 * collections on the entity and thereby delete the source rows without firing the RoleChangeInterceptor - so nothing
 * queued the user for recalculation and current_assignment kept the roles forever.
 * <p>
 * Every production RoleChangeHook is @Profile("!test") and RoleChangeInterceptor is itself a mock here (see
 * TestInterceptorConfiguration), so the queued recalculation cannot be observed as a side effect. The tests assert on
 * the intercepted calls instead, and run the recalculation explicitly to show the end state.
 * <p>
 * The basic test data is expensive to build, so each test reuses one data set across the cases it covers.
 */
class UserServiceDisabledUserCleanupIntegrationTest extends BaseIntegrationTest {

	@Autowired
	private UserService userService;

	@Autowired
	private SettingsService settingsService;

	@Autowired
	private AssignmentService assignmentService;

	@Autowired
	private BasicTestDataFactory testDataFactory;

	@Autowired
	private RoleChangeInterceptor roleChangeInterceptor;

	@BeforeEach
	void login() {
		SecurityUtil.loginSystemAccount();

		// the mock is a singleton in the test context, and clearInvocations (not reset) keeps its stubbing
		clearInvocations(roleChangeInterceptor);
	}

	@AfterEach
	void logout() {
		SecurityUtil.logoutSystemAccount();
	}

	@Test
	@DisplayName("A disabled and a deleted user past the cutoff both have the direct assignments removed through the intercepted methods, and a recalculation then drops them while keeping the inherited ones")
	void removesDirectAssignmentsThroughTheInterceptedMethods() {
		BasicTestData data = testDataFactory.createBasicTestData();
		String disabledUserUuid = data.user().getUuid();
		long directUserRoleId = data.urDirectlyAssigned().getId();
		long userRoleFromDirectRoleGroupId = data.urViaRgDirectlyAssigned().getId();
		long inheritedUserRoleId = data.urViaParentOUInherited().getId();
		// the deleted branch of the query keys off lastUpdated rather than disabledAt, and is the resigned-employee case
		String deletedUserUuid = createDeletedUserWithAssignments(data, LocalDate.now().minusDays(40));
		disableUser(userService.getByUuid(disabledUserUuid), LocalDate.now().minusDays(40));
		settingsService.setRemoveDirectAssignmentsForDisabled(30);

		userService.cleanupDisabledUserAssignments();

		// clearing the collections fired neither of these - that was the bug: no hook, no recalculation
		verify(roleChangeInterceptor).interceptRemoveUserRoleAssignmentOnUser2(argThat(isUser(disabledUserUuid)), any());
		verify(roleChangeInterceptor).interceptRemoveRoleGroupAssignmentOnUser2(argThat(isUser(disabledUserUuid)), any());
		verify(roleChangeInterceptor).interceptRemoveUserRoleAssignmentOnUser2(argThat(isUser(deletedUserUuid)), any());
		verify(roleChangeInterceptor).interceptRemoveRoleGroupAssignmentOnUser2(argThat(isUser(deletedUserUuid)), any());

		flushAndClear();
		assertNoDirectAssignments(disabledUserUuid, "disabled user");
		assertNoDirectAssignments(deletedUserUuid, "deleted user");

		// in production this is done by the recalculation the interceptor queues
		User cleanedUser = userService.getByUuid(disabledUserUuid);
		testDataFactory.updateUserAssignmentCalculation(cleanedUser);
		flushAndClear();

		Set<CurrentAssignment> assignments = assignmentService.getByUserIncludingInactive(userService.getByUuid(disabledUserUuid));
		assertThat(userRoleIds(assignments))
				.doesNotContain(directUserRoleId, userRoleFromDirectRoleGroupId)
				.contains(inheritedUserRoleId);
	}

	@Test
	@DisplayName("The assignments are left alone while the user is enabled, while the grace period has not passed, and when a blank threshold falls back to the 180 day default")
	void keepsAssignmentsUntilTheGracePeriodHasPassed() {
		BasicTestData data = testDataFactory.createBasicTestData();
		String userUuid = data.user().getUuid();

		settingsService.setRemoveDirectAssignmentsForDisabled(0);
		userService.cleanupDisabledUserAssignments();
		assertAssignmentsIntact(userUuid, "enabled user, no matter how old the account is");

		disableUser(userService.getByUuid(userUuid), LocalDate.now().minusDays(10));
		settingsService.setRemoveDirectAssignmentsForDisabled(30);
		userService.cleanupDisabledUserAssignments();
		assertAssignmentsIntact(userUuid, "disabled inside the grace period");

		disableUser(userService.getByUuid(userUuid), LocalDate.now().minusDays(40));
		settingsService.setRemoveDirectAssignmentsForDisabled(null);
		userService.cleanupDisabledUserAssignments();
		assertAssignmentsIntact(userUuid, "blank threshold, so the 180 day default applies");

		verify(roleChangeInterceptor, never()).interceptRemoveUserRoleAssignmentOnUser2(argThat(isUser(userUuid)), any());
		verify(roleChangeInterceptor, never()).interceptRemoveRoleGroupAssignmentOnUser2(argThat(isUser(userUuid)), any());
	}

	@Test
	@DisplayName("Cleaning up a user that no longer exists is a no-op rather than a failure")
	void cleanupOfUnknownUserIsANoOp() {
		UserService.AssignmentCleanupResult result = userService.cleanupAssignmentsForDisabledUser("no-such-user-uuid");

		assertThat(result.removedAnything()).isFalse();
		verify(roleChangeInterceptor, never()).interceptRemoveUserRoleAssignmentOnUser2(any(), any());
	}

	private static ArgumentMatcher<User> isUser(String userUuid) {
		return user -> user != null && userUuid.equals(user.getUuid());
	}

	/**
	 * Reuses the roles from the basic test data, as building a second full data set is expensive. lastUpdated is
	 * @UpdateTimestamp, so it has to be backdated with a bulk update after the entity is written.
	 */
	private String createDeletedUserWithAssignments(BasicTestData data, LocalDate lastUpdated) {
		String uuid = UUID.randomUUID().toString();
		User user = testDataFactory.createUser(uuid, "deleted-user-id", "Deleted user", data.user().getDomain());
		testDataFactory.assignUserRoleToUser(data.urDirectlyAssigned().getIdentifier(), user);
		testDataFactory.assignRoleGroupToUser(user, data.rgDirectlyAssigned());
		user.setDeleted(true);
		flushAndClear();

		entityManager.createQuery("update users u set u.lastUpdated = :lastUpdated where u.uuid = :uuid")
				.setParameter("lastUpdated", Date.from(lastUpdated.atStartOfDay(ZoneId.systemDefault()).toInstant()))
				.setParameter("uuid", uuid)
				.executeUpdate();
		flushAndClear();

		return uuid;
	}

	private void assertNoDirectAssignments(String userUuid, String scenario) {
		// including deleted, as getByUuid filters those out
		User cleanedUser = userService.getOptionalByUuidIncludingDeleted(userUuid).orElseThrow();
		assertThat(cleanedUser.getUserRoleAssignments()).as(scenario).isEmpty();
		assertThat(cleanedUser.getRoleGroupAssignments()).as(scenario).isEmpty();
	}

	private void disableUser(User user, LocalDate disabledAt) {
		user.setDisabled(true);
		user.setDisabledAt(disabledAt);
		userService.save(user);
		flushAndClear();
	}

	private void assertAssignmentsIntact(String userUuid, String scenario) {
		flushAndClear();

		User untouchedUser = userService.getByUuid(userUuid);
		assertThat(untouchedUser.getUserRoleAssignments()).as(scenario).hasSize(1);
		assertThat(untouchedUser.getRoleGroupAssignments()).as(scenario).hasSize(1);
	}

	private Set<Long> userRoleIds(Set<CurrentAssignment> assignments) {
		return assignments.stream()
				.filter(assignment -> assignment.getUserRole() != null)
				.map(assignment -> assignment.getUserRole().getId())
				.collect(Collectors.toSet());
	}
}
