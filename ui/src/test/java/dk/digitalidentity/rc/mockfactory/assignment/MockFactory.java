package dk.digitalidentity.rc.mockfactory.assignment;

import dk.digitalidentity.rc.dao.model.ConstraintType;
import dk.digitalidentity.rc.dao.model.Function;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.ManagerSubstitute;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.OrgUnitAssignment;
import dk.digitalidentity.rc.dao.model.OrgUnitRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.OrgUnitUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.PostponedConstraint;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.RoleGroupUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.SystemRole;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignment;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignmentConstraintValue;
import dk.digitalidentity.rc.dao.model.Title;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserOUFunction;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.UserRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.UserUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.assignment.CurrentAssignment;
import dk.digitalidentity.rc.dao.model.assignment.CurrentAssignmentPostponedConstraint;
import dk.digitalidentity.rc.dao.model.assignment.CurrentExceptedAssignment;
import dk.digitalidentity.rc.dao.model.enums.ConstraintUIType;
import dk.digitalidentity.rc.dao.model.enums.ConstraintValueType;
import dk.digitalidentity.rc.dao.model.enums.ContainsTitles;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MockFactory {


	public static User createUser(String uuid) {
		User user = new User();
		user.setUuid(uuid);
		user.setUserRoleAssignments(new ArrayList<>());
		user.setRoleGroupAssignments(new ArrayList<>());
		user.setPositions(new ArrayList<>());
		user.setSubstituteFor(new ArrayList<>());
		return user;
	}

	public static  ItSystem createItSystem(String uuid) {
		ItSystem itSystem = new ItSystem();
		itSystem.setUuid(uuid);
		return itSystem;
	}

	public static  UserRole createUserRole(String uuid, ItSystem itSystem) {
		UserRole userRole = new UserRole();
		userRole.setUuid(uuid);
		userRole.setItSystem(itSystem);
		return userRole;
	}

	public static  RoleGroup createRoleGroup(Long id, List<UserRole> userRoles) {
		RoleGroup roleGroup = new RoleGroup();
		roleGroup.setId(id);
		roleGroup.setUserRoleAssignments(
			userRoles.stream()
				.map(ur -> {
					var ura = new dk.digitalidentity.rc.dao.model.RoleGroupUserRoleAssignment();
					ura.setUserRole(ur);
					return ura;
				})
				.toList()
		);
		return roleGroup;
	}

	public static CurrentAssignment createCurrentAssignment(Long id, String recordHash, User user) {
		CurrentAssignment assignment = new CurrentAssignment();
		assignment.setId(id);
		assignment.setUser(user);
		assignment.setItSystem(createItSystem());
		assignment.setUserRole(createUserRole());
		assignment.setRecordHash(recordHash);
		return assignment;
	}

	public static ItSystem createItSystem() {
		ItSystem itSystem = new ItSystem();
		itSystem.setId(1L);
		itSystem.setUuid("it-system-uuid-123");
		return itSystem;
	}

	public static UserRole createUserRole() {
		UserRole userRole = new UserRole();
		userRole.setId(1L);
		userRole.setUuid("user-uuid-123");
		return userRole;
	}

	public static UserUserRoleAssignment createDirectUserRoleAssignment(UserRole userRole, LocalDate startDate, LocalDate stopDate) {
		UserUserRoleAssignment assignment = new UserUserRoleAssignment();
		assignment.setId(1L);
		assignment.setUserRole(userRole);
		assignment.setStartDate(startDate);
		assignment.setStopDate(stopDate);
		assignment.setPostponedConstraints(List.of());
		return assignment;
	}

	public static UserRoleGroupAssignment createDirectRoleGroupAssignment(RoleGroup roleGroup, LocalDate startDate, LocalDate stopDate) {
		UserRoleGroupAssignment assignment = new UserRoleGroupAssignment();
		assignment.setId(1L);
		assignment.setRoleGroup(roleGroup);
		assignment.setStartDate(startDate);
		assignment.setStopDate(stopDate);
		return assignment;
	}

	public static OrgUnit createOrgUnit(String uuid, OrgUnit parent) {
		OrgUnit orgUnit = new OrgUnit();
		orgUnit.setUuid(uuid);
		orgUnit.setParent(parent);
		orgUnit.setUserRoleAssignments(new ArrayList<>());
		orgUnit.setRoleGroupAssignments(new ArrayList<>());
		return orgUnit;
	}

	public static Title createTitle(String uuid) {
		Title title = new Title();
		title.setUuid(uuid);
		return title;
	}

	public static Position createPosition(OrgUnit orgUnit, Title title,User user, boolean doNotInherit) {
		Position position = new Position();
		position.setOrgUnit(orgUnit);
		position.setTitle(title);
		position.setDoNotInherit(doNotInherit);
		position.setUser(user);
		return position;
	}

	public static OrgUnitUserRoleAssignment createOrgUnitUserRoleAssignment(UserRole userRole, OrgUnit orgUnit) {
		OrgUnitUserRoleAssignment assignment = new OrgUnitUserRoleAssignment();
		assignment.setUserRole(userRole);
		assignment.setOrgUnit(orgUnit);
		return assignment;
	}

	public static OrgUnitRoleGroupAssignment createOrgUnitRoleGroupAssignment(RoleGroup roleGroup, OrgUnit orgUnit) {
		OrgUnitRoleGroupAssignment assignment = new OrgUnitRoleGroupAssignment();
		assignment.setRoleGroup(roleGroup);
		assignment.setOrgUnit(orgUnit);
		return assignment;
	}

	public static OrgUnitUserRoleAssignment createExcludedUsersAssignment(boolean containsExceptedUsers, List<User> exceptedUsers, OrgUnit orgUnit) {
		OrgUnitUserRoleAssignment assignment = new OrgUnitUserRoleAssignment();
		assignment.setContainsExceptedUsers(containsExceptedUsers);
		assignment.setExceptedUsers(exceptedUsers);
		assignment.setOrgUnit(orgUnit);
		return assignment;
	}

	public static OrgUnitAssignment createOrgunitAssignment(OrgUnit orgUnit, UserRole userRole, List<Title> titles) {
		OrgUnitUserRoleAssignment orgUnitAssignment = new OrgUnitUserRoleAssignment();
		orgUnitAssignment.setOrgUnit(orgUnit);
		orgUnitAssignment.setUserRole(userRole);
		orgUnitAssignment.setTitles(titles);
		return orgUnitAssignment;
	}

	public static CurrentExceptedAssignment createCurrentExceptedAssignment(Long id, String recordHash, String userUuid) {
		CurrentExceptedAssignment assignment = new CurrentExceptedAssignment();
		assignment.setId(id);
		assignment.setRecordHash(recordHash);
		assignment.setExceptionUserUuid(userUuid);
		assignment.setExceptionAssignmentId(1L);
		assignment.setExceptionUserRoleId(1L);
		assignment.setExceptionItSystemId(1L);
		return assignment;
	}

	public static ManagerSubstitute createManagerSubstitute(User manager, User substitute, OrgUnit orgUnit) {
		ManagerSubstitute managerSubstitute = new ManagerSubstitute();
		managerSubstitute.setManager(manager);
		managerSubstitute.setSubstitute(substitute);
		managerSubstitute.setOrgUnit(orgUnit);
		return managerSubstitute;
	}

	public static OrgUnit createOrgUnitWithManager(String uuid, OrgUnit parent, User manager) {
		OrgUnit orgUnit = createOrgUnit(uuid, parent);
		orgUnit.setManager(manager);
		return orgUnit;
	}

	public static OrgUnitUserRoleAssignment createManagerAssignment(OrgUnit orgUnit, boolean isManager, boolean isSubstitutes, boolean inherit) {
		OrgUnitUserRoleAssignment assignment = new OrgUnitUserRoleAssignment();
		assignment.setOrgUnit(orgUnit);
		assignment.setManager(isManager);
		assignment.setSubstitutes(isSubstitutes);
		assignment.setInherit(inherit);
		return assignment;
	}

	public static Function createFunction(String uuid) {
		Function function = new Function();
		function.setUuid(uuid);
		function.setName("Function " + uuid);
		function.setActive(true);
		return function;
	}

	public static UserOUFunction createUserOUFunction(User user, OrgUnit orgUnit, Function function) {
		UserOUFunction userOUFunction = new UserOUFunction();
		userOUFunction.setUser(user);
		userOUFunction.setOrgUnit(orgUnit);
		userOUFunction.setFunction(function);
		return userOUFunction;
	}

	public static OrgUnitUserRoleAssignment createFunctionAssignment(OrgUnit orgUnit, List<Function> functions, boolean containsFunctions, boolean inherit) {
		OrgUnitUserRoleAssignment assignment = new OrgUnitUserRoleAssignment();
		assignment.setOrgUnit(orgUnit);
		assignment.setFunctions(functions);
		assignment.setContainsFunctions(containsFunctions);
		assignment.setInherit(inherit);
		return assignment;
	}

	public static CurrentAssignment createCurrentAssignmentWithRoleGroup(User user, UserRole userRole, RoleGroup roleGroup, OrgUnit orgUnit, long assignmentId) {
		CurrentAssignment assignment = new CurrentAssignment();
		assignment.setUser(user);
		assignment.setUserRole(userRole);
		assignment.setItSystem(userRole.getItSystem());
		assignment.setRoleGroup(roleGroup);
		assignment.setOrgUnit(orgUnit);
		assignment.setAssignmentId(assignmentId);
		return assignment;
	}

	public static CurrentAssignment createFullCurrentAssignment(User user, UserRole userRole, ItSystem itSystem, OrgUnit orgUnit, Title title, RoleGroup roleGroup) {
		CurrentAssignment assignment = new CurrentAssignment();
		assignment.setUser(user);
		assignment.setUserRole(userRole);
		assignment.setItSystem(itSystem);
		assignment.setOrgUnit(orgUnit);
		assignment.setTitle(title);
		assignment.setRoleGroup(roleGroup);
		assignment.setAssignmentId(1L);
		assignment.setAssignedBy("Test User (test-user)");
		assignment.setPostponedConstraints(new java.util.HashSet<>());
		assignment.setCreatedAt(java.time.LocalDateTime.now());
		assignment.setUpdatedAt(java.time.LocalDateTime.now());
		assignment.setRecordHash("test-hash");
		return assignment;
	}

	public static ConstraintType createConstraintType(String uuid, String name, String entityId) {
		ConstraintType constraintType = new ConstraintType();
		constraintType.setId(1L);
		constraintType.setUuid(uuid);
		constraintType.setName(name);
		constraintType.setEntityId(entityId);
		constraintType.setUiType(ConstraintUIType.COMBO_SINGLE);
		return constraintType;
	}

	public static SystemRole createSystemRole(Long id) {
		SystemRole systemRole = new SystemRole();
		systemRole.setId(id);
		systemRole.setUuid("system-role-uuid-" + id);
		systemRole.setName("System Role " + id);
		return systemRole;
	}

	public static PostponedConstraint createPostponedConstraint(String value, ConstraintType constraintType, SystemRole systemRole) {
		PostponedConstraint constraint = new PostponedConstraint();
		constraint.setValue(value);
		constraint.setConstraintType(constraintType);
		constraint.setSystemRole(systemRole);
		return constraint;
	}

	public static ItSystem createItSystem(long id, String name) {
		ItSystem itSystem = new ItSystem();
		itSystem.setId(id);
		itSystem.setName(name);
		itSystem.setIdentifier(name.toLowerCase().replace(" ", "-"));
		itSystem.setAttestationExempt(false);
		return itSystem;
	}

	public static UserRole createUserRole(long id, String name, ItSystem itSystem) {
		UserRole userRole = new UserRole();
		userRole.setId(id);
		userRole.setName(name);
		userRole.setIdentifier(name.toLowerCase().replace(" ", "-"));
		userRole.setItSystem(itSystem);
		userRole.setSensitiveRole(false);
		userRole.setExtraSensitiveRole(false);
		userRole.setRoleAssignmentAttestationByAttestationResponsible(false);
		return userRole;
	}

	public static OrgUnit createNamedOrgUnit(String uuid, String name) {
		OrgUnit orgUnit = new OrgUnit();
		orgUnit.setUuid(uuid);
		orgUnit.setName(name);
		orgUnit.setUserRoleAssignments(new ArrayList<>());
		orgUnit.setRoleGroupAssignments(new ArrayList<>());
		return orgUnit;
	}

	public static RoleGroup createRoleGroup(long id, String name, String description, List<UserRole> userRoles) {
		RoleGroup roleGroup = new RoleGroup();
		roleGroup.setId(id);
		roleGroup.setName(name);
		roleGroup.setDescription(description);
		roleGroup.setUserRoleAssignments(
			userRoles.stream()
				.map(ur -> {
					RoleGroupUserRoleAssignment ura = new RoleGroupUserRoleAssignment();
					ura.setUserRole(ur);
					return ura;
				})
				.toList()
		);
		return roleGroup;
	}

	public static OrgUnitUserRoleAssignment createOrgUnitUserRoleAssignment(UserRole userRole, OrgUnit orgUnit,
			boolean manager, boolean substitutes, boolean inherit) {
		OrgUnitUserRoleAssignment assignment = new OrgUnitUserRoleAssignment();
		assignment.setUserRole(userRole);
		assignment.setOrgUnit(orgUnit);
		assignment.setManager(manager);
		assignment.setSubstitutes(substitutes);
		assignment.setInherit(inherit);
		assignment.setContainsExceptedUsers(false);
		assignment.setContainsTitles(ContainsTitles.NO);
		assignment.setContainsFunctions(false);
		return assignment;
	}

	public static OrgUnitRoleGroupAssignment createOrgUnitRoleGroupAssignment(RoleGroup roleGroup, OrgUnit orgUnit,
			boolean manager, boolean substitutes, boolean inherit) {
		OrgUnitRoleGroupAssignment assignment = new OrgUnitRoleGroupAssignment();
		assignment.setRoleGroup(roleGroup);
		assignment.setOrgUnit(orgUnit);
		assignment.setManager(manager);
		assignment.setSubstitutes(substitutes);
		assignment.setInherit(inherit);
		assignment.setContainsExceptedUsers(false);
		assignment.containsTitles = ContainsTitles.NO;
		assignment.setContainsFunctions(false);
		return assignment;
	}

	public static SystemRole createSystemRole(long id, String name) {
		SystemRole systemRole = new SystemRole();
		systemRole.setId(id);
		systemRole.setName(name);
		systemRole.setDescription("Description of " + name);
		systemRole.setUuid("system-role-uuid-" + id);
		return systemRole;
	}

	public static SystemRoleAssignment createSystemRoleAssignment(SystemRole systemRole) {
		SystemRoleAssignment assignment = new SystemRoleAssignment();
		assignment.setSystemRole(systemRole);
		assignment.setConstraintValues(new ArrayList<>());
		return assignment;
	}

	public static CurrentAssignmentPostponedConstraint createPostponedConstraint(String typeUuid, String typeName, String typeEntityId, List<String> values) {
		CurrentAssignmentPostponedConstraint constraint = new CurrentAssignmentPostponedConstraint();
		constraint.setConstraintTypeUuid(typeUuid);
		constraint.setConstraintTypeName(typeName);
		constraint.setConstraintTypeEntityId(typeEntityId);
		constraint.setConstraintTypeUIType(ConstraintUIType.COMBO_SINGLE);
		constraint.setConstraintTypeId(1L);
		constraint.setSystemRoleId(1L);
		constraint.setValue(values);
		return constraint;
	}

	public static SystemRoleAssignmentConstraintValue createConstraintValue(String constraintName, ConstraintValueType valueType, String value) {
		ConstraintType constraintType = new ConstraintType();
		constraintType.setName(constraintName);
		constraintType.setUuid("constraint-type-uuid-" + constraintName);
		constraintType.setEntityId("entity-id-" + constraintName);
		constraintType.setUiType(dk.digitalidentity.rc.dao.model.enums.ConstraintUIType.COMBO_SINGLE);

		SystemRoleAssignmentConstraintValue cv = new SystemRoleAssignmentConstraintValue();
		cv.setConstraintType(constraintType);
		cv.setConstraintValueType(valueType);
		cv.setConstraintValue(value);
		return cv;
	}

	public static SystemRole createSystemRoleWithWeight(long id, int weight, ItSystem itSystem) {
		SystemRole systemRole = createSystemRole(id, "System Role " + id);
		systemRole.setWeight(weight);
		systemRole.setItSystem(itSystem);
		return systemRole;
	}

	public static UserRole createUserRoleWithSystemRoles(String uuid, ItSystem itSystem, SystemRole... systemRoles) {
		UserRole userRole = createUserRole(uuid, itSystem);
		userRole.setSystemRoleAssignments(
			Arrays.stream(systemRoles)
				.map(MockFactory::createSystemRoleAssignment)
				.toList()
		);
		return userRole;
	}

	public static CurrentAssignment createCurrentAssignmentForUser(User user, UserRole userRole) {
		CurrentAssignment assignment = new CurrentAssignment();
		assignment.setUser(user);
		assignment.setUserRole(userRole);
		assignment.setItSystem(userRole.getItSystem());
		return assignment;
	}
}
