package dk.digitalidentity.rc.test.integration.rolerequest;

import org.springframework.security.access.AccessDeniedException;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.controller.mvc.datatables.dao.CombinedRoleViewDatatableDao;
import dk.digitalidentity.rc.controller.mvc.datatables.dao.UserRoleViewDatatableDao;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.RoleGroupUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import dk.digitalidentity.rc.dao.model.enums.OrgUnitLevel;
import dk.digitalidentity.rc.dao.model.enums.SystemRoleLinkType;
import dk.digitalidentity.rc.rolerequest.model.enums.RequestableBy;
import dk.digitalidentity.rc.rolerequest.model.permission.RequesterPermission;
import dk.digitalidentity.rc.rolerequest.service.permission.RequestableRoleService;
import dk.digitalidentity.rc.rolerequest.service.permission.RequesterContext;
import dk.digitalidentity.rc.service.DomainService;
import dk.digitalidentity.rc.service.SettingsService;
import dk.digitalidentity.rc.test.integration.setup.BaseIntegrationTest;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.context.TestConstructor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@RequiredArgsConstructor
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@DisplayName("RequesterRule, applied through the role views")
class RequesterRuleIntegrationTest extends BaseIntegrationTest {

	private enum Authority { ADMIN, SELF, MANAGER, AUTH_RESPONSIBLE, AUTHORIZED }

	private final RequestableRoleService requestableRoleService;
	private final UserRoleViewDatatableDao userRoleViewDao;
	private final CombinedRoleViewDatatableDao combinedRoleViewDao;
	private final SettingsService settingsService;
	private final DomainService domainService;

	private OrgUnit parentOu;
	private OrgUnit receiverOu;
	private ItSystem itSystem;

	@BeforeEach
	void setUp() {
		settingsService.setRolerequestRequester(RequesterPermission.global(List.of(RequestableBy.EMPLOYEE)));
		parentOu = orgUnit("Forvaltning", null);
		receiverOu = orgUnit("Afdeling", parentOu);
		itSystem = itSystem(RequesterPermission.inherit());
	}

	@Nested
	@DisplayName("Inheritance")
	class Inheritance {

		@Test
		@DisplayName("a role that inherits uses its IT system's permission")
		void roleInheritsItSystem() {
			// ---- Given ---- //
			itSystem.setRequesterPermission(RequesterPermission.of(List.of(RequestableBy.MANAGERORSUBSTITUTE)));
			UserRole role = userRole(RequesterPermission.inherit());

			// ---- When / Then ---- //
			assertThat(mayRequest(role, context(Authority.MANAGER))).isTrue();
		}

		@Test
		@DisplayName("a role and IT system that both inherit use the global permission")
		void roleAndItSystemInheritGlobal() {
			// ---- Given ---- //
			UserRole role = userRole(RequesterPermission.inherit());

			// ---- When / Then ---- //
			assertThat(mayRequest(role, context(Authority.SELF))).isTrue();
		}

		@Test
		@DisplayName("an empty stored role permission inherits")
		void emptyStoredValueInherits() {
			// ---- Given ---- //
			itSystem.setRequesterPermission(RequesterPermission.of(List.of(RequestableBy.MANAGERORSUBSTITUTE)));
			UserRole role = userRole(RequesterPermission.none());
			flushAndClear();
			entityManager.createNativeQuery("UPDATE user_roles SET requester_permission = '' WHERE id = :id")
				.setParameter("id", role.getId())
				.executeUpdate();

			// ---- When / Then ---- //
			assertThat(mayRequest(role, context(Authority.MANAGER))).isTrue();
		}

		@Test
		@DisplayName("an empty global setting means NONE, also for admins")
		void emptyGlobalIsNone() {
			// ---- Given ---- //
			UserRole role = userRole(RequesterPermission.inherit());
			flushAndClear();
			entityManager.createNativeQuery("UPDATE setting SET setting_value = '' WHERE setting_key = 'allowedrequesters'")
				.executeUpdate();

			// ---- When / Then ---- //
			assertThat(mayRequest(role, context(Authority.ADMIN))).isFalse();
		}
	}

	@Nested
	@DisplayName("Admins")
	class Admins {

		@Test
		@DisplayName("may request a role that does not list ADMIN")
		void adminBypassesValues() {
			// ---- Given ---- //
			UserRole role = userRole(RequesterPermission.of(List.of(RequestableBy.EMPLOYEE)));

			// ---- When / Then ---- //
			assertThat(mayRequest(role, context(Authority.ADMIN))).isTrue();
		}

		@Test
		@DisplayName("may not request a role set to NONE")
		void noneBlocksAdmin() {
			// ---- Given ---- //
			UserRole role = userRole(RequesterPermission.none());

			// ---- When / Then ---- //
			assertThat(mayRequest(role, context(Authority.ADMIN))).isFalse();
		}
	}

	@Nested
	@DisplayName("Authorities")
	class Authorities {

		@ParameterizedTest(name = "{0} is allowed by {1}")
		@CsvSource({ "EMPLOYEE, SELF", "MANAGERORSUBSTITUTE, MANAGER", "AUTHRESPONSIBLE, AUTH_RESPONSIBLE", "AUTHORIZED, AUTHORIZED" })
		@DisplayName("each value is allowed by its own authority")
		void valueAllowedByOwnAuthority(RequestableBy value, Authority authority) {
			// ---- Given ---- //
			UserRole role = userRole(RequesterPermission.of(List.of(value)));

			// ---- When / Then ---- //
			assertThat(mayRequest(role, context(authority))).isTrue();
		}

		@ParameterizedTest(name = "{0} is not allowed without {1}")
		@CsvSource({ "EMPLOYEE, SELF", "MANAGERORSUBSTITUTE, MANAGER", "AUTHRESPONSIBLE, AUTH_RESPONSIBLE", "AUTHORIZED, AUTHORIZED" })
		@DisplayName("each value is not allowed by the other non-admin authorities")
		void valueNotAllowedByOtherAuthorities(RequestableBy value, Authority authority) {
			// ---- Given ---- //
			UserRole role = userRole(RequesterPermission.of(List.of(value)));
			EnumSet<Authority> otherAuthorities = EnumSet.complementOf(EnumSet.of(authority, Authority.ADMIN));

			// ---- When / Then ---- //
			assertThat(mayRequest(role, context(otherAuthorities.toArray(Authority[]::new)))).isFalse();
		}
	}

	@Nested
	@DisplayName("Gates")
	class Gates {

		@Test
		@DisplayName("a read-only role cannot be requested")
		void readOnlyBlocks() {
			// ---- Given ---- //
			UserRole role = userRole(RequesterPermission.of(List.of(RequestableBy.EMPLOYEE)));
			role.setReadOnly(true);

			// ---- When / Then ---- //
			assertThat(mayRequest(role, context(Authority.SELF))).isFalse();
		}

		@Test
		@DisplayName("an enabled OU filter without OUs allows nothing")
		void enabledEmptyOuFilterBlocks() {
			// ---- Given ---- //
			UserRole role = userRole(RequesterPermission.of(List.of(RequestableBy.EMPLOYEE)));
			role.setOuFilterEnabled(true);

			// ---- When / Then ---- //
			assertThat(mayRequest(role, context(Authority.SELF))).isFalse();
		}

		@Test
		@DisplayName("a user role OU filter includes the descendants of its OUs")
		void userRoleOuFilterIncludesDescendants() {
			// ---- Given ---- //
			UserRole role = userRole(RequesterPermission.of(List.of(RequestableBy.EMPLOYEE)));
			role.setOuFilterEnabled(true);
			role.setOrgUnitFilterOrgUnits(new ArrayList<>(List.of(parentOu)));

			// ---- When / Then ---- //
			assertThat(mayRequest(role, context(Authority.SELF))).isTrue();
		}

		@Test
		@DisplayName("a role group OU filter includes the descendants of its OUs")
		void roleGroupOuFilterIncludesDescendants() {
			// ---- Given ---- //
			RoleGroup roleGroup = roleGroup(RequesterPermission.of(List.of(RequestableBy.EMPLOYEE)), userRole(RequesterPermission.inherit()));
			roleGroup.setOuFilterEnabled(true);
			roleGroup.setOrgUnitFilterOrgUnits(new ArrayList<>(List.of(parentOu)));

			// ---- When / Then ---- //
			assertThat(mayRequest(roleGroup, context(Authority.SELF))).isTrue();
		}
	}

	@Nested
	@DisplayName("AUTHORIZED IT-system scope")
	class AuthorizedScope {

		@Test
		@DisplayName("a user role outside the scope cannot be requested")
		void userRoleOutsideScope() {
			// ---- Given ---- //
			UserRole role = userRole(RequesterPermission.of(List.of(RequestableBy.AUTHORIZED)));

			// ---- When / Then ---- //
			assertThat(mayRequest(role, authorizedFor(Set.of(-1L)))).isFalse();
		}

		@Test
		@DisplayName("a role group with every IT system in scope can be requested")
		void roleGroupInsideScope() {
			// ---- Given ---- //
			RoleGroup roleGroup = roleGroup(RequesterPermission.of(List.of(RequestableBy.AUTHORIZED)), userRole(RequesterPermission.inherit()));

			// ---- When / Then ---- //
			assertThat(mayRequest(roleGroup, authorizedFor(Set.of(itSystem.getId())))).isTrue();
		}

		@Test
		@DisplayName("a role group with one IT system outside the scope cannot be requested")
		void roleGroupPartlyOutsideScope() {
			// ---- Given ---- //
			ItSystem otherItSystem = itSystem(RequesterPermission.inherit());
			UserRole otherRole = userRole(RequesterPermission.inherit());
			otherRole.setItSystem(otherItSystem);
			RoleGroup roleGroup = roleGroup(RequesterPermission.of(List.of(RequestableBy.AUTHORIZED)), userRole(RequesterPermission.inherit()), otherRole);

			// ---- When / Then ---- //
			assertThat(mayRequest(roleGroup, authorizedFor(Set.of(itSystem.getId())))).isFalse();
		}
	}

	@Nested
	@DisplayName("OU filters")
	class OuFilters {

		@Test
		@DisplayName("a disabled role OU filter is ignored, even if stale OUs remain")
		void disabledRoleFilterIgnored() {
			// ---- Given ---- //
			UserRole role = userRole(RequesterPermission.of(List.of(RequestableBy.EMPLOYEE)));
			role.setOrgUnitFilterOrgUnits(new ArrayList<>(List.of(orgUnit("Andet sted", null))));

			// ---- When / Then ---- //
			assertThat(mayRequest(role, context(Authority.SELF))).isTrue();
		}

		@Test
		@DisplayName("a disabled IT-system OU filter is ignored, even if stale OUs remain")
		void disabledItSystemFilterIgnored() {
			// ---- Given ---- //
			itSystem.setOrgUnitFilterOrgUnits(new ArrayList<>(List.of(orgUnit("Andet sted", null))));
			UserRole role = userRole(RequesterPermission.of(List.of(RequestableBy.EMPLOYEE)));

			// ---- When / Then ---- //
			assertThat(mayRequest(role, context(Authority.SELF))).isTrue();
		}

		@Test
		@DisplayName("an enabled IT-system OU filter blocks a receiver outside its OUs")
		void itSystemFilterBlocksOutside() {
			// ---- Given ---- //
			itSystem.setOuFilterEnabled(true);
			itSystem.setOrgUnitFilterOrgUnits(new ArrayList<>(List.of(orgUnit("Andet sted", null))));
			UserRole role = userRole(RequesterPermission.of(List.of(RequestableBy.EMPLOYEE)));

			// ---- When / Then ---- //
			assertThat(mayRequest(role, context(Authority.SELF))).isFalse();
		}

		@Test
		@DisplayName("an enabled IT-system OU filter allows a receiver below one of its OUs")
		void itSystemFilterAllowsInside() {
			// ---- Given ---- //
			itSystem.setOuFilterEnabled(true);
			itSystem.setOrgUnitFilterOrgUnits(new ArrayList<>(List.of(parentOu)));
			UserRole role = userRole(RequesterPermission.of(List.of(RequestableBy.EMPLOYEE)));

			// ---- When / Then ---- //
			assertThat(mayRequest(role, context(Authority.SELF))).isTrue();
		}

		@Test
		@DisplayName("a receiver without an OU is blocked by an enabled role OU filter")
		void receiverWithoutOrgUnitBlocked() {
			// ---- Given ---- //
			UserRole role = userRole(RequesterPermission.of(List.of(RequestableBy.EMPLOYEE)));
			role.setOuFilterEnabled(true);
			role.setOrgUnitFilterOrgUnits(new ArrayList<>(List.of(parentOu)));

			// ---- When / Then ---- //
			assertThat(mayRequest(role, contextWithoutOrgUnit(Authority.SELF))).isFalse();
		}

		@Test
		@DisplayName("a role group OU filter allows the receiver's own OU")
		void roleGroupFilterAllowsOwnOrgUnit() {
			// ---- Given ---- //
			RoleGroup roleGroup = roleGroup(RequesterPermission.of(List.of(RequestableBy.EMPLOYEE)), userRole(RequesterPermission.inherit()));
			roleGroup.setOuFilterEnabled(true);
			roleGroup.setOrgUnitFilterOrgUnits(new ArrayList<>(List.of(receiverOu)));

			// ---- When / Then ---- //
			assertThat(mayRequest(roleGroup, context(Authority.SELF))).isTrue();
		}

		@Test
		@DisplayName("a receiver without an OU is blocked by an enabled role group OU filter")
		void roleGroupReceiverWithoutOrgUnitBlocked() {
			// ---- Given ---- //
			RoleGroup roleGroup = roleGroup(RequesterPermission.of(List.of(RequestableBy.EMPLOYEE)), userRole(RequesterPermission.inherit()));
			roleGroup.setOuFilterEnabled(true);
			roleGroup.setOrgUnitFilterOrgUnits(new ArrayList<>(List.of(receiverOu)));

			// ---- When / Then ---- //
			assertThat(mayRequest(roleGroup, contextWithoutOrgUnit(Authority.SELF))).isFalse();
		}
	}

	@Nested
	@DisplayName("Precedence")
	class Precedence {

		@Test
		@DisplayName("an explicit role permission is used instead of the IT system's")
		void explicitRoleBeatsItSystem() {
			// ---- Given ---- //
			itSystem.setRequesterPermission(RequesterPermission.of(List.of(RequestableBy.MANAGERORSUBSTITUTE)));
			UserRole role = userRole(RequesterPermission.of(List.of(RequestableBy.EMPLOYEE)));

			// ---- When / Then ---- //
			assertThat(mayRequest(role, context(Authority.MANAGER))).isFalse();
		}

		@Test
		@DisplayName("an IT system set to NONE blocks the roles that inherit from it")
		void itSystemNoneBlocksInheritingRole() {
			// ---- Given ---- //
			itSystem.setRequesterPermission(RequesterPermission.none());
			UserRole role = userRole(RequesterPermission.inherit());

			// ---- When / Then ---- //
			assertThat(mayRequest(role, context(Authority.SELF))).isFalse();
		}

		@Test
		@DisplayName("a role group that inherits uses the global permission")
		void roleGroupInheritsGlobal() {
			// ---- Given ---- //
			RoleGroup roleGroup = roleGroup(RequesterPermission.inherit(), userRole(RequesterPermission.inherit()));

			// ---- When / Then ---- //
			assertThat(mayRequest(roleGroup, context(Authority.SELF))).isTrue();
		}
	}

	@Nested
	@DisplayName("Removal")
	class Removal {
		private User employee;

		@BeforeEach
		void createEmployee() {
			employee = user();
			position(employee, receiverOu);
		}

		@Test
		@DisplayName("a user role can be requested removed even when the OU filter excludes all of the receiver's OUs")
		void userRoleRemovalIgnoresOuFilter() {
			// ---- Given ---- //
			UserRole role = userRole(RequesterPermission.of(List.of(RequestableBy.EMPLOYEE)));
			role.setOuFilterEnabled(true);
			role.setOrgUnitFilterOrgUnits(new ArrayList<>(List.of(orgUnit("Andet sted", null))));
			flushAndClear();

			// ---- When / Then ---- //
			assertThat(requestableRoleService.removableUserRoleIds(employee, employee, List.of(role.getId()))).containsExactly(role.getId());
		}

		@Test
		@DisplayName("a role group can be requested removed even when its OU filter excludes the receiver's OUs")
		void roleGroupRemovalIgnoresOuFilter() {
			// ---- Given ---- //
			RoleGroup roleGroup = roleGroup(RequesterPermission.of(List.of(RequestableBy.EMPLOYEE)), userRole(RequesterPermission.inherit()));
			roleGroup.setOuFilterEnabled(true);
			roleGroup.setOrgUnitFilterOrgUnits(new ArrayList<>(List.of(orgUnit("Andet sted", null))));
			flushAndClear();

			// ---- When / Then ---- //
			assertThat(requestableRoleService.removableRoleGroupIds(employee, employee, List.of(roleGroup.getId()))).containsExactly(roleGroup.getId());
		}

		@Test
		@DisplayName("a user role in a deleted IT system can still be requested removed")
		void userRoleInDeletedItSystem() {
			// ---- Given ---- //
			ItSystem deletedItSystem = itSystem(RequesterPermission.inherit());
			deletedItSystem.setDeleted(true);
			UserRole role = userRole(RequesterPermission.of(List.of(RequestableBy.EMPLOYEE)));
			role.setItSystem(deletedItSystem);
			flushAndClear();

			// ---- When / Then ---- //
			assertThat(requestableRoleService.removableUserRoleIds(employee, employee, List.of(role.getId()))).containsExactly(role.getId());
		}

		@Test
		@DisplayName("a receiver without any OU can still have a role requested removed")
		void receiverWithoutOrgUnits() {
			// ---- Given ---- //
			User withoutPositions = user();
			UserRole role = userRole(RequesterPermission.of(List.of(RequestableBy.EMPLOYEE)));
			flushAndClear();

			// ---- When / Then ---- //
			assertThat(requestableRoleService.removableUserRoleIds(withoutPositions, withoutPositions, List.of(role.getId()))).containsExactly(role.getId());
		}

		@Test
		@DisplayName("removal is refused when the receiver does not hold the user role directly")
		void userRoleNotDirectlyAssigned() {
			// ---- Given ---- //
			UserRole role = userRole(RequesterPermission.of(List.of(RequestableBy.EMPLOYEE)));
			flushAndClear();

			// ---- When / Then ---- //
			assertThatThrownBy(() -> requestableRoleService.canRequestRemoval(employee, employee, role))
				.isInstanceOf(AccessDeniedException.class);
		}

		@Test
		@DisplayName("removal is refused when the receiver does not hold the role group directly")
		void roleGroupNotDirectlyAssigned() {
			// ---- Given ---- //
			RoleGroup roleGroup = roleGroup(RequesterPermission.of(List.of(RequestableBy.EMPLOYEE)), userRole(RequesterPermission.inherit()));
			flushAndClear();

			// ---- When / Then ---- //
			assertThatThrownBy(() -> requestableRoleService.canRequestRemoval(employee, employee, roleGroup))
				.isInstanceOf(AccessDeniedException.class);
		}
	}

	private RequesterContext contextWithoutOrgUnit(Authority... context) {
		RequesterContext withOrgUnit = context(context);
		return new RequesterContext(withOrgUnit.admin(), withOrgUnit.self(), withOrgUnit.managerOrSubstitute(), withOrgUnit.authResponsible(),
			withOrgUnit.authorized(), null, null, List.of(), true);
	}

	private User user() {
		User user = new User();
		user.setUuid(UUID.randomUUID().toString());
		user.setExtUuid(UUID.randomUUID().toString());
		user.setUserId("user-" + UUID.randomUUID());
		user.setName("Medarbejder");
		user.setDomain(domainService.getPrimaryDomain());
		user.setPositions(new ArrayList<>());
		entityManager.persist(user);
		return user;
	}

	private void position(User user, OrgUnit positionOrgUnit) {
		Position position = new Position();
		position.setName("Sagsbehandler");
		position.setUser(user);
		position.setOrgUnit(positionOrgUnit);
		entityManager.persist(position);
		user.getPositions().add(position);
	}

	private boolean mayRequest(UserRole role, RequesterContext context) {
		flushAndClear();
		return !userRoleViewDao.findIds(requestableRoleService.userRoles(context)
			.and((root, _, cb) -> cb.equal(root.get("id"), role.getId()))).isEmpty();
	}

	private boolean mayRequest(RoleGroup roleGroup, RequesterContext context) {
		flushAndClear();
		return !combinedRoleViewDao.findIds(requestableRoleService.roleGroups(context)
			.and((root, _, cb) -> cb.equal(root.get("id"), roleGroup.getId()))).isEmpty();
	}

	private RequesterContext context(Authority... authorities) {
		List<Authority> present = Arrays.asList(authorities);
		return new RequesterContext(
			present.contains(Authority.ADMIN),
			present.contains(Authority.SELF),
			present.contains(Authority.MANAGER),
			present.contains(Authority.AUTH_RESPONSIBLE),
			present.contains(Authority.AUTHORIZED),
			null,
			receiverOu.getUuid(),
			List.of(receiverOu.getUuid(), parentOu.getUuid()),
			true
		);
	}

	private RequesterContext authorizedFor(Set<Long> itSystemIds) {
		return new RequesterContext(false, false, false, false, true, itSystemIds,
			receiverOu.getUuid(), List.of(receiverOu.getUuid(), parentOu.getUuid()), true);
	}

	private ItSystem itSystem(RequesterPermission requesterPermission) {
		ItSystem system = new ItSystem();
		system.setUuid(UUID.randomUUID().toString());
		system.setName("Økonomisystem");
		system.setIdentifier("it-system-" + UUID.randomUUID());
		system.setSystemType(ItSystemType.MANUAL);
		system.setLastUpdated(new Date());
		system.setDomain(domainService.getPrimaryDomain());
		system.setRequesterPermission(requesterPermission);
		entityManager.persist(system);
		return system;
	}

	private UserRole userRole(RequesterPermission requesterPermission) {
		UserRole role = new UserRole();
		role.setUuid(UUID.randomUUID().toString());
		role.setName("Bogholder");
		role.setIdentifier("role-" + UUID.randomUUID());
		role.setItSystem(itSystem);
		role.setSystemRoleLinkType(SystemRoleLinkType.NONE);
		role.setSystemRoleAssignments(new ArrayList<>());
		role.setOrgUnitFilterOrgUnits(new ArrayList<>());
		role.setDescription("Bogføring");
		role.setRequesterPermission(requesterPermission);
		entityManager.persist(role);
		return role;
	}

	private RoleGroup roleGroup(RequesterPermission requesterPermission, UserRole... userRoles) {
		RoleGroup roleGroup = new RoleGroup();
		roleGroup.setName("Økonomi");
		roleGroup.setRequesterPermission(requesterPermission);
		roleGroup.setUserRoleAssignments(new ArrayList<>());
		roleGroup.setOrgUnitFilterOrgUnits(new ArrayList<>());
		entityManager.persist(roleGroup);
		for (UserRole userRole : userRoles) {
			RoleGroupUserRoleAssignment assignment = new RoleGroupUserRoleAssignment();
			assignment.setRoleGroup(roleGroup);
			assignment.setUserRole(userRole);
			assignment.setAssignedByUserId("test");
			assignment.setAssignedByName("Test");
			assignment.setAssignedTimestamp(new Date());
			entityManager.persist(assignment);
			roleGroup.getUserRoleAssignments().add(assignment);
		}
		return roleGroup;
	}

	private OrgUnit orgUnit(String name, OrgUnit parent) {
		OrgUnit orgUnit = new OrgUnit();
		orgUnit.setUuid(UUID.randomUUID().toString());
		orgUnit.setName(name);
		orgUnit.setActive(true);
		orgUnit.setLevel(OrgUnitLevel.NONE);
		orgUnit.setParent(parent);
		orgUnit.setChildren(new ArrayList<>());
		orgUnit.setUserRoleAssignments(new ArrayList<>());
		orgUnit.setRoleGroupAssignments(new ArrayList<>());
		orgUnit.setKles(new ArrayList<>());
		orgUnit.setTitles(new ArrayList<>());
		orgUnit.setFunctionAssignments(new ArrayList<>());
		orgUnit.setAuthorizationManagers(new ArrayList<>());
		entityManager.persist(orgUnit);
		return orgUnit;
	}
}
