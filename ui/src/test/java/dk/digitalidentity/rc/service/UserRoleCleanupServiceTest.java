package dk.digitalidentity.rc.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Date;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestConstructor;

import dk.digitalidentity.rc.dao.DomainDao;
import dk.digitalidentity.rc.dao.model.Domain;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.UserUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import dk.digitalidentity.rc.dao.model.enums.SystemRoleLinkType;
import dk.digitalidentity.rc.test.integration.setup.BaseIntegrationTest;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class UserRoleCleanupServiceTest extends BaseIntegrationTest {

	private final UserRoleCleanupService userRoleCleanupService;
	private final UserRoleService userRoleService;
	private final DomainDao domainDao;

	@Test
	@DisplayName("Reproducerer #31: user-role med direkte bruger-tildeling kan slettes uden TransientPropertyValueException")
	void canDeleteUserRoleWithDirectUserAssignment() {
		SecurityContextHolder.clearContext();

		Domain domain = domainDao.findByName("Administrativt");
		ItSystem itSystem = persistItSystem(domain);
		UserRole userRole = persistUserRole(itSystem);
		User user = persistUser(domain);
		persistDirectAssignment(user, userRole);

		flushAndClear();

		// Re-load user i en ny session og rør userRoleAssignments-kollektionen,
		// så UserUserRoleAssignment er en managed entity der peger på UserRole'en —
		// det er den tilstand der trigger fejlen fra #31.
		User reloadedUser = entityManager.find(User.class, user.getUuid());
		assertThat(reloadedUser.getUserRoleAssignments())
			.as("setup sanity: user skal have en direkte UserRole-tildeling")
			.hasSize(1);

		long userRoleId = userRole.getId();
		userRoleCleanupService.deleteWithCleanup(userRoleService.getById(userRoleId));
		flushAndClear();

		assertThat(userRoleService.getOptionalById(userRoleId))
			.as("UserRole skal være slettet")
			.isEmpty();

		User afterDelete = entityManager.find(User.class, user.getUuid());
		assertThat(afterDelete.getUserRoleAssignments())
			.as("bruger må ikke længere referere den slettede UserRole")
			.isEmpty();
	}

	private ItSystem persistItSystem(Domain domain) {
		ItSystem itSystem = new ItSystem();
		itSystem.setUuid(UUID.randomUUID().toString());
		itSystem.setName("Test IT System");
		itSystem.setIdentifier("test-it-system-" + UUID.randomUUID());
		itSystem.setSystemType(ItSystemType.MANUAL);
		itSystem.setLastUpdated(new Date());
		itSystem.setDomain(domain);
		entityManager.persist(itSystem);
		return itSystem;
	}

	private UserRole persistUserRole(ItSystem itSystem) {
		UserRole role = new UserRole();
		role.setUuid(UUID.randomUUID().toString());
		role.setName("Role to delete");
		role.setIdentifier("role-to-delete-" + UUID.randomUUID());
		role.setItSystem(itSystem);
		role.setSystemRoleLinkType(SystemRoleLinkType.NONE);
		role.setSystemRoleAssignments(new ArrayList<>());
		role.setDescription("Test description");
		entityManager.persist(role);
		return role;
	}

	private User persistUser(Domain domain) {
		User user = new User();
		user.setUuid(UUID.randomUUID().toString());
		user.setUserId("cleanup-test-user");
		user.setName("Cleanup Test User");
		user.setPositions(new ArrayList<>());
		user.setUserRoleAssignments(new ArrayList<>());
		user.setRoleGroupAssignments(new ArrayList<>());
		user.setAltAccounts(new ArrayList<>());
		user.setKles(new ArrayList<>());
		user.setManagerSubstitutes(new ArrayList<>());
		user.setSubstituteFor(new ArrayList<>());
		user.setFunctionAssignments(new ArrayList<>());
		user.setDomain(domain);
		user.setExtUuid(UUID.randomUUID().toString());
		entityManager.persist(user);
		return user;
	}

	private void persistDirectAssignment(User user, UserRole userRole) {
		UserUserRoleAssignment assignment = new UserUserRoleAssignment();
		assignment.setUser(user);
		assignment.setUserRole(userRole);
		assignment.setAssignedByUserId("test");
		assignment.setAssignedByName("Test Setup");
		assignment.setAssignedTimestamp(new Date());
		assignment.setPostponedConstraints(new ArrayList<>());
		entityManager.persist(assignment);
		user.getUserRoleAssignments().add(assignment);
	}
}
