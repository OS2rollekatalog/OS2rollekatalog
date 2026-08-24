package dk.digitalidentity.rc.service.entraid;

import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.test.integration.setup.BaseIntegrationTest;
import dk.digitalidentity.rc.test.integration.setup.BasicTestDataFactory;
import dk.digitalidentity.rc.test.integration.setup.BasicTestDataFactory.BasicTestData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression coverage for the EntraID backSync persistence path. backSync runs in a @Scheduled task
 * (no open-session-in-view) on detached entities, so the per-user assignment changes go through
 * dedicated transactional methods that re-load a managed user. The earlier "save the detached user
 * in the loop" approach re-inserted earlier assignments (their generated id stays 0) and could throw
 * LazyInitializationException on the remove branch - these tests guard against that regression.
 */
class EntraIDServiceBackSyncIntegrationTest extends BaseIntegrationTest {

	@Autowired
	private EntraIDService entraIDService;

	@Autowired
	private BasicTestDataFactory testDataFactory;

	@BeforeEach
	void login() {
		SecurityUtil.loginSystemAccount();
	}

	@AfterEach
	void logout() {
		SecurityUtil.logoutSystemAccount();
	}

	@Test
	void assignPersistsTheDirectAssignment() {
		BasicTestData data = testDataFactory.createBasicTestData();
		String userUuid = data.user().getUuid();
		// a role that is NOT directly assigned to the user (only via an OU)
		long userRoleId = data.urViaParentOUInherited().getId();

		assertThat(countDirectAssignments(userUuid, userRoleId)).isZero();

		entraIDService.assignUserRoleTransactional(userUuid, userRoleId);
		flushAndClear();

		assertThat(countDirectAssignments(userUuid, userRoleId)).isEqualTo(1);
	}

	@Test
	void assignIsIdempotentAndDoesNotDuplicate() {
		BasicTestData data = testDataFactory.createBasicTestData();
		String userUuid = data.user().getUuid();
		long userRoleId = data.urViaParentOUInherited().getId();

		entraIDService.assignUserRoleTransactional(userUuid, userRoleId);
		flushAndClear();
		// calling it again (as a later backSync run / another role iteration would) must not
		// create a second row - this is the duplicate-insert regression we guard against
		entraIDService.assignUserRoleTransactional(userUuid, userRoleId);
		flushAndClear();

		assertThat(countDirectAssignments(userUuid, userRoleId)).isEqualTo(1);
	}

	@Test
	void removeDeletesTheDirectAssignment() {
		BasicTestData data = testDataFactory.createBasicTestData();
		String userUuid = data.user().getUuid();
		long userRoleId = data.urViaParentOUInherited().getId();

		entraIDService.assignUserRoleTransactional(userUuid, userRoleId);
		flushAndClear();
		assertThat(countDirectAssignments(userUuid, userRoleId)).isEqualTo(1);

		entraIDService.removeUserRoleTransactional(userUuid, userRoleId);
		flushAndClear();

		assertThat(countDirectAssignments(userUuid, userRoleId)).isZero();
	}

	private long countDirectAssignments(String userUuid, long userRoleId) {
		return entityManager.createQuery(
				"select count(a) from user_roles_mapping a where a.user.uuid = :uuid and a.userRole.id = :roleId",
				Long.class)
			.setParameter("uuid", userUuid)
			.setParameter("roleId", userRoleId)
			.getSingleResult();
	}
}
