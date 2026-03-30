package dk.digitalidentity.rc.test.integration.setup;

import dk.digitalidentity.rc.dao.DomainDao;
import dk.digitalidentity.rc.dao.UserRoleDao;
import dk.digitalidentity.rc.dao.model.Domain;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.OrgUnitRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.OrgUnitUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.RoleGroupUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.SystemRole;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignment;
import dk.digitalidentity.rc.dao.model.Title;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.UserRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.UserUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.enums.ContainsTitles;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import dk.digitalidentity.rc.dao.model.enums.OrgUnitLevel;
import dk.digitalidentity.rc.dao.model.enums.RoleType;
import dk.digitalidentity.rc.dao.model.enums.SystemRoleLinkType;
import dk.digitalidentity.rc.event.AssignmentChangeEventHandlerService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.test.context.TestConstructor;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Creates test data for integration tests.
 */
@RequiredArgsConstructor
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@Component
@Profile("test")
public class BasicTestDataFactory {

	private final EntityManager entityManager;
	private final DomainDao domainDao;
	private final UserRoleDao userRoleDao;

	@Autowired
	private AssignmentChangeEventHandlerService assignmentChangeEventHandlerService;

	public record BasicTestData(
		User user,
		OrgUnit parentOrgUnit,
		OrgUnit childOrgUnit,
		ItSystem itSystem,
		UserRole urDirectlyAssigned,
		UserRole urViaParentOUInherited,
		UserRole urViaParentOUNotInherited,
		UserRole urViaChildOU,
		UserRole urNegativeViaChildOU,
		UserRole urViaRgDirectlyAssigned,
		UserRole urViaRgViaParentOUInherited,
		UserRole urViaRgViaParentOUNotInherited,
		UserRole urViaRgViaChildOU,
		UserRole urViaRgNegativeViaChildOU,
		RoleGroup rgDirectlyAssigned,
		RoleGroup rgViaParentOUInherited,
		RoleGroup rgViaParentOUNotInherited,
		RoleGroup rgViaChildOU,
		RoleGroup rgNegativeViaChildOU
	) {
	}

	/**
	 * Creates testdata in the database containing:<br>
	 * - 1 IT system with 10 system roles, each with a corresponding userrole<br>
	 * - 5 standalone userroles assigned as follows:<br>
	 *   - 1 directly to the user<br>
	 *   - 1 to the parent OU WITH inheritance<br>
	 *   - 1 to the parent OU WITHOUT inheritance<br>
	 *   - 1 to the child OU WITHOUT inheritance<br>
	 *   - 1 NEGATIVE to the child OU (excluded via title)<br>
	 * - 5 rolegroups (each containing 1 userrole linked to 1 systemrole) assigned as follows:<br>
	 *   - 1 directly to the user<br>
	 *   - 1 to the parent OU WITH inheritance<br>
	 *   - 1 to the parent OU WITHOUT inheritance<br>
	 *   - 1 to the child OU WITHOUT inheritance<br>
	 *   - 1 NEGATIVE to the child OU (excluded via title)<br>
	 * - 2 OUs, where one is a child of the other<br>
	 * - 1 Title assigned to the user's position (used for negative assignments)<br>
	 * - 1 User with a position in the child OU<br>
	 */
	public BasicTestData createBasicTestData() {
		Domain domain = getPrimaryDomain();

		ItSystem itSystem = createItSystem(domain);

		// Standalone userroles (UR) with their system roles (SR)
		UserRole urDirectlyAssigned = createUserRole("UR Directly Assigned", itSystem);
		UserRole urViaParentOUInherited = createUserRole("UR Via Parent OU Inherited", itSystem);
		UserRole urViaParentOUNotInherited = createUserRole("UR Via Parent OU Not Inherited", itSystem);
		UserRole urViaChildOU = createUserRole("UR Via Child OU", itSystem);
		UserRole urNegativeViaChildOU = createUserRole("UR Negative Via Child OU", itSystem);

		linkSystemRoleToUserRole(createSystemRole("SR UR Directly Assigned", itSystem), urDirectlyAssigned);
		linkSystemRoleToUserRole(createSystemRole("SR UR Via Parent OU Inherited", itSystem), urViaParentOUInherited);
		linkSystemRoleToUserRole(createSystemRole("SR UR Via Parent OU Not Inherited", itSystem), urViaParentOUNotInherited);
		linkSystemRoleToUserRole(createSystemRole("SR UR Via Child OU", itSystem), urViaChildOU);
		linkSystemRoleToUserRole(createSystemRole("SR UR Negative Via Child OU", itSystem), urNegativeViaChildOU);

		// Rolegroups (RG), each containing a userrole (UR in RG) linked to a system role (SR in RG)
		UserRole urViaRgDirectlyAssigned = createUserRoleInRoleGroup("UR in RG Directly Assigned", "SR in RG Directly Assigned", itSystem);
		RoleGroup rgDirectlyAssigned = createRoleGroup("RG Directly Assigned", urViaRgDirectlyAssigned);

		UserRole urViaRgViaParentOUInherited = createUserRoleInRoleGroup("UR in RG Directly Assigned", "SR in RG Directly Assigned", itSystem);
		RoleGroup rgViaParentOUInherited = createRoleGroup("RG Via Parent OU Inherited",urViaRgViaParentOUInherited);

		UserRole urViaRgViaParentOUNotInherited = createUserRoleInRoleGroup("UR in RG Directly Assigned", "SR in RG Directly Assigned", itSystem);
		RoleGroup rgViaParentOUNotInherited = createRoleGroup("RG Via Parent OU Not Inherited",urViaRgViaParentOUNotInherited);

		UserRole urViaRgViaChildOU = createUserRoleInRoleGroup("UR in RG Directly Assigned", "SR in RG Directly Assigned", itSystem);
		RoleGroup rgViaChildOU = createRoleGroup("RG Via Child OU",urViaRgViaChildOU);

		UserRole urViaRgNegativeViaChildOU = createUserRoleInRoleGroup("UR in RG Negative Via Child OU", "SR in RG Negative Via Child OU", itSystem);
		RoleGroup rgNegativeViaChildOU = createRoleGroup("RG Negative Via Child OU", urViaRgNegativeViaChildOU);

		// Title for negative assignments
		Title title = createTitle("Test Title");

		// Org unit hierarchy
		OrgUnit parentOrgUnit = createOrgUnit("Parent OrgUnit", null);
		OrgUnit childOrgUnit = createOrgUnit("Child OrgUnit", parentOrgUnit);

		// Standalone userrole assignments to OUs
		assignUserRoleToOrgUnit(parentOrgUnit, urViaParentOUInherited, true);
		assignUserRoleToOrgUnit(parentOrgUnit, urViaParentOUNotInherited, false);
		assignUserRoleToOrgUnit(childOrgUnit, urViaChildOU, false);
		assignNegativeUserRoleToOrgUnit(childOrgUnit, urNegativeViaChildOU, title);

		// Rolegroup assignments to OUs
		assignRoleGroupToOrgUnit(parentOrgUnit, rgViaParentOUInherited, true);
		assignRoleGroupToOrgUnit(parentOrgUnit, rgViaParentOUNotInherited, false);
		assignRoleGroupToOrgUnit(childOrgUnit, rgViaChildOU, false);
		assignNegativeRoleGroupToOrgUnit(childOrgUnit, rgNegativeViaChildOU, title);

		// User with position in child OU (with title for negative assignments) + direct assignments
		User user = createUser(domain);
		createPosition(user, childOrgUnit, title);
		assignUserRoleToUser(user, urDirectlyAssigned);
		assignRoleGroupToUser(user, rgDirectlyAssigned);

		entityManager.flush();

		updateUserAssignmentCalculation(user);

		return new BasicTestData(
			user, parentOrgUnit, childOrgUnit, itSystem,
			urDirectlyAssigned, urViaParentOUInherited, urViaParentOUNotInherited, urViaChildOU, urNegativeViaChildOU,
			urViaRgDirectlyAssigned, urViaRgViaParentOUInherited, urViaRgViaParentOUNotInherited, urViaRgViaChildOU, urViaRgNegativeViaChildOU,
			rgDirectlyAssigned, rgViaParentOUInherited, rgViaParentOUNotInherited, rgViaChildOU, rgNegativeViaChildOU
		);
	}

	public UserRole assignUserRoleToUser(String userRoleIdentifier, User user) {
		UserRole userRole = userRoleDao.getByIdentifier(userRoleIdentifier);
		assignUserRoleToUser(user, userRole);
		entityManager.flush();
		return userRole;
	}

	public void updateUserAssignmentCalculation(User user) {
		assignmentChangeEventHandlerService.updateUser(user.getUuid());
	}

	private ItSystem createItSystem(Domain domain) {
		ItSystem itSystem = new ItSystem();
		itSystem.setUuid(UUID.randomUUID().toString());
		itSystem.setName("Test IT System");
		itSystem.setIdentifier("test-it-system");
		itSystem.setSystemType(ItSystemType.MANUAL);
		itSystem.setLastUpdated(new Date());
		itSystem.setDomain(domain);
		entityManager.persist(itSystem);
		return itSystem;
	}

	private UserRole createUserRole(String name, ItSystem itSystem) {
		UserRole role = new UserRole();
		role.setUuid(UUID.randomUUID().toString());
		role.setName(name);
		role.setIdentifier(name.toLowerCase().replace(" ", "-"));
		role.setItSystem(itSystem);
		role.setSystemRoleLinkType(SystemRoleLinkType.NONE);
		role.setSystemRoleAssignments(new ArrayList<>());
		role.setDescription("Test description");
		entityManager.persist(role);
		return role;
	}

	private UserRole createUserRoleInRoleGroup(String userRoleName, String systemRoleName, ItSystem itSystem) {
		UserRole userRole = createUserRole(userRoleName, itSystem);
		linkSystemRoleToUserRole(createSystemRole(systemRoleName, itSystem), userRole);
		return userRole;
	}

	private RoleGroup createRoleGroup(String name, UserRole userRole) {
		RoleGroup roleGroup = new RoleGroup();
		roleGroup.setName(name);
		roleGroup.setUserRoleAssignments(new ArrayList<>());
		entityManager.persist(roleGroup);

		RoleGroupUserRoleAssignment rgAssignment = new RoleGroupUserRoleAssignment();
		rgAssignment.setRoleGroup(roleGroup);
		rgAssignment.setUserRole(userRole);
		rgAssignment.setAssignedByUserId("test");
		rgAssignment.setAssignedByName("Test Setup");
		rgAssignment.setAssignedTimestamp(new Date());
		entityManager.persist(rgAssignment);
		roleGroup.getUserRoleAssignments().add(rgAssignment);

		return roleGroup;
	}

	private OrgUnit createOrgUnit(String name, OrgUnit parent) {
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
		entityManager.persist(orgUnit);
		if (parent != null) {
			parent.getChildren().add(orgUnit);
		}
		return orgUnit;
	}

	private void assignUserRoleToOrgUnit(OrgUnit orgUnit, UserRole userRole, boolean inherit) {
		OrgUnitUserRoleAssignment assignment = new OrgUnitUserRoleAssignment();
		assignment.setOrgUnit(orgUnit);
		assignment.setUserRole(userRole);
		assignment.setInherit(inherit);
		assignment.setAssignedByUserId("test");
		assignment.setAssignedByName("Test Setup");
		assignment.setAssignedTimestamp(new Date());
		assignment.setExceptedUsers(new ArrayList<>());
		assignment.setTitles(new ArrayList<>());
		assignment.setFunctions(new ArrayList<>());
		entityManager.persist(assignment);
		orgUnit.getUserRoleAssignments().add(assignment);
	}

	private void assignNegativeUserRoleToOrgUnit(OrgUnit orgUnit, UserRole userRole, Title title) {
		OrgUnitUserRoleAssignment assignment = new OrgUnitUserRoleAssignment();
		assignment.setOrgUnit(orgUnit);
		assignment.setUserRole(userRole);
		assignment.setInherit(false);
		assignment.setAssignedByUserId("test");
		assignment.setAssignedByName("Test Setup");
		assignment.setAssignedTimestamp(new Date());
		assignment.setExceptedUsers(new ArrayList<>());
		assignment.setTitles(new ArrayList<>(List.of(title)));
		assignment.setFunctions(new ArrayList<>());
		assignment.setContainsTitles(ContainsTitles.NEGATIVE);
		entityManager.persist(assignment);
		orgUnit.getUserRoleAssignments().add(assignment);
	}

	private void assignRoleGroupToOrgUnit(OrgUnit orgUnit, RoleGroup roleGroup, boolean inherit) {
		OrgUnitRoleGroupAssignment assignment = new OrgUnitRoleGroupAssignment();
		assignment.setOrgUnit(orgUnit);
		assignment.setRoleGroup(roleGroup);
		assignment.setInherit(inherit);
		assignment.setAssignedByUserId("test");
		assignment.setAssignedByName("Test Setup");
		assignment.setAssignedTimestamp(new Date());
		assignment.setExceptedUsers(new ArrayList<>());
		assignment.setTitles(new ArrayList<>());
		assignment.setFunctions(new ArrayList<>());
		assignment.setContainsTitles(ContainsTitles.NO);
		entityManager.persist(assignment);
		orgUnit.getRoleGroupAssignments().add(assignment);
	}

	private void assignNegativeRoleGroupToOrgUnit(OrgUnit orgUnit, RoleGroup roleGroup, Title title) {
		OrgUnitRoleGroupAssignment assignment = new OrgUnitRoleGroupAssignment();
		assignment.setOrgUnit(orgUnit);
		assignment.setRoleGroup(roleGroup);
		assignment.setInherit(false);
		assignment.setAssignedByUserId("test");
		assignment.setAssignedByName("Test Setup");
		assignment.setAssignedTimestamp(new Date());
		assignment.setExceptedUsers(new ArrayList<>());
		assignment.setTitles(new ArrayList<>(List.of(title)));
		assignment.setFunctions(new ArrayList<>());
		assignment.setContainsTitles(ContainsTitles.NEGATIVE);
		entityManager.persist(assignment);
		orgUnit.getRoleGroupAssignments().add(assignment);
	}

	private Domain getPrimaryDomain() {
		return domainDao.findByName("Administrativt");
	}

	private User createUser(Domain domain) {
		User user = createUser("test-user-uuid", "test-user-id", "Test User", domain);
		entityManager.persist(user);
		return user;
	}

	public User createUser(String uuid, String id, String name, Domain domain) {
		User user = new User();
		user.setUuid(uuid);
		user.setUserId(id);
		user.setName(name);
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

	private void createPosition(User user, OrgUnit orgUnit, Title title) {
		Position position = new Position();
		position.setName("Test Position");
		position.setOrgUnit(orgUnit);
		position.setUser(user);
		position.setTitle(title);
		entityManager.persist(position);
		user.getPositions().add(position);
	}

	private Title createTitle(String name) {
		Title title = new Title();
		title.setUuid(UUID.randomUUID().toString());
		title.setName(name);
		title.setActive(true);
		entityManager.persist(title);
		return title;
	}

	private SystemRole createSystemRole(String name, ItSystem itSystem) {
		SystemRole systemRole = new SystemRole();
		systemRole.setUuid(UUID.randomUUID().toString());
		systemRole.setName(name);
		systemRole.setIdentifier(name.toLowerCase().replace(" ", "-"));
		systemRole.setItSystem(itSystem);
		systemRole.setRoleType(RoleType.BOTH);
		systemRole.setWeight(1);
		entityManager.persist(systemRole);
		return systemRole;
	}

	private void linkSystemRoleToUserRole(SystemRole systemRole, UserRole userRole) {
		SystemRoleAssignment assignment = new SystemRoleAssignment();
		assignment.setUserRole(userRole);
		assignment.setSystemRole(systemRole);
		assignment.setAssignedByUserId("test");
		assignment.setAssignedByName("Test Setup");
		assignment.setAssignedTimestamp(new Date());
		assignment.setConstraintValues(new ArrayList<>());
		userRole.getSystemRoleAssignments().add(assignment);
	}

	private void assignUserRoleToUser(User user, UserRole userRole) {
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

	private void assignRoleGroupToUser(User user, RoleGroup roleGroup) {
		UserRoleGroupAssignment assignment = new UserRoleGroupAssignment();
		assignment.setUser(user);
		assignment.setRoleGroup(roleGroup);
		assignment.setAssignedByUserId("test");
		assignment.setAssignedByName("Test Setup");
		assignment.setAssignedTimestamp(new Date());
		entityManager.persist(assignment);
		user.getRoleGroupAssignments().add(assignment);
	}

}
