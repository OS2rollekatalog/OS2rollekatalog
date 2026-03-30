package dk.digitalidentity.rc.mockfactory.security;

import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.dao.model.ConstraintType;
import dk.digitalidentity.rc.dao.model.SystemRole;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignment;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignmentConstraintValue;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.enums.ConstraintUIType;
import dk.digitalidentity.rc.dao.model.enums.ConstraintValueType;
import dk.digitalidentity.samlmodule.model.SamlGrantedAuthority;
import dk.digitalidentity.samlmodule.model.TokenUser;

import java.util.HashMap;
import java.util.List;

public class MockFactory {
	public static final String ATTRIBUTE_USERID = "ATTRIBUTE_USERID";
	public static final String ATTRIBUTE_NAME = "ATTRIBUTE_NAME";
	public static final String ATTRIBUTE_SUBSTITUTE_FOR = "ATTRIBUTE_SUBSTITUTE_FOR";
	public static final String ATTRIBUTE_CLIENT = "ATTRIBUTE_CLIENT";
	public static final String ATTRIBUTE_USER_UUID = "ATTRIBUTE_USER_UUID";


	public static TokenUser mockTokenUser(String id, String name, String uuid, List<String> authorities, List<String> substituteFor) {
		TokenUser principal = TokenUser.builder()
			.cvr("12345678")
			.username(id)
			.authorities(authorities.stream().map(SamlGrantedAuthority::new).toList())
			.attributes(new HashMap<>())
			.build();

		principal.getAttributes().put(ATTRIBUTE_USERID, id);
		principal.getAttributes().put(ATTRIBUTE_USER_UUID, uuid);
		principal.getAttributes().put(ATTRIBUTE_NAME, name);
		principal.getAttributes().put(ATTRIBUTE_SUBSTITUTE_FOR, substituteFor);

		return principal;
	}

	public static UserRole createUserRoleWithSystemRole(String identifier) {
		SystemRole systemRole = new SystemRole();
		systemRole.setIdentifier(identifier);

		SystemRoleAssignment systemRoleAssignment = new SystemRoleAssignment();
		systemRoleAssignment.setSystemRole(systemRole);

		UserRole userRole = new UserRole();
		userRole.setSystemRoleAssignments(List.of(systemRoleAssignment));

		return userRole;
	}

	public static UserRole createUserRoleWithSystemRole(String identifier, List<SystemRoleAssignmentConstraintValue> constraintValue) {
		SystemRole systemRole = new SystemRole();
		systemRole.setIdentifier(identifier);

		SystemRoleAssignment systemRoleAssignment = new SystemRoleAssignment();
		systemRoleAssignment.setConstraintValues(constraintValue);
		systemRoleAssignment.setSystemRole(systemRole);

		UserRole userRole = new UserRole();
		userRole.setSystemRoleAssignments(List.of(systemRoleAssignment));

		return userRole;
	}

	public static SystemRoleAssignmentConstraintValue createConstraint(ConstraintType constraintType, String value) {

		SystemRoleAssignmentConstraintValue constraintValue = new SystemRoleAssignmentConstraintValue();
		constraintValue.setConstraintType(constraintType);
		constraintValue.setConstraintValue(value);
		constraintValue.setConstraintValueType(ConstraintValueType.VALUE);
		return constraintValue;
	}

	public static ConstraintType createOUConstraintType() {
		ConstraintType constraintType = new ConstraintType();
		constraintType.setUuid("orgunit-constraint-type");
		constraintType.setEntityId(Constants.INTERNAL_ORGUNIT_CONSTRAINT_ENTITY_ID);
		constraintType.setUiType(ConstraintUIType.REGEX);
		return constraintType;
	}

	public static ConstraintType createITSystemConstraintType() {
		ConstraintType constraintType = new ConstraintType();
		constraintType.setUuid("itsystem-constraint-type");
		constraintType.setEntityId(Constants.INTERNAL_ITSYSTEM_CONSTRAINT_ENTITY_ID);
		constraintType.setUiType(ConstraintUIType.REGEX);
		return constraintType;
	}
}
