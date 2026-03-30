package dk.digitalidentity.rc.bootstrap.dev;

import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.dao.model.ConstraintType;
import dk.digitalidentity.rc.dao.model.ConstraintTypeSupport;
import dk.digitalidentity.rc.dao.model.SystemRole;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignment;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignmentConstraintValue;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.enums.ConstraintValueType;
import dk.digitalidentity.rc.service.ConstraintTypeService;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.SystemRoleService;
import dk.digitalidentity.rc.service.UserRoleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevSeedRoles {

	record ConstraintValueDef(String constraintEntityId, ConstraintValueType valueType, String value) {}

	record UserRoleDef(String identifier, String name, String description, String systemRoleIdentifier, List<ConstraintValueDef> constraints) {}

	// Realistic constraint values keyed by constraint entityId
	private static final Map<String, String> CONSTRAINT_SAMPLE_VALUES = Map.of(
		Constants.KLE_CONSTRAINT_ENTITY_ID, "27.18.00",
		Constants.PNUMBER_CONSTRAINT_ENTITY_ID, "1234567890",
		Constants.SENUMBER_CONSTRAINT_ENTITY_ID, "12345678"
	);

	private static final List<UserRoleDef> USER_ROLES = List.of(new UserRoleDef(
		"test-rc-role-1",
		"RC Testrolle 1",
		"...",
		Constants.ROLE_USERROLE_READ_ID,
		List.of()

	));

	private final ConstraintTypeService constraintTypeService;
	private final ItSystemService itSystemService;
	private final SystemRoleService systemRoleService;
	private final UserRoleService userRoleService;

	public void seed(Map<String, Long> systemRoleIdsByIdentifier) {
		log.info("Seeding user roles...");

		// Build a combined lookup that also includes system roles from pre-existing IT systems (e.g. Role Catalogue)
		HashMap<String, Long> allSystemRoleIds = new HashMap<>(systemRoleIdsByIdentifier);
		itSystemService.findByIdentifier(Constants.ROLE_CATALOGUE_IDENTIFIER).stream()
			.flatMap(itSystem -> systemRoleService.getByItSystem(itSystem).stream())
			.forEach(sr -> allSystemRoleIds.put(sr.getIdentifier(), sr.getId()));

		Map<Long, SystemRole> systemRolesById = systemRoleService.getByIdsAsMap(systemRoleIdsByIdentifier.values());

		for (Map.Entry<String, Long> entry : systemRoleIdsByIdentifier.entrySet()) {
			SystemRole systemRole = systemRolesById.get(entry.getValue());
			long itSystemId = systemRole.getItSystem().getId();

			createUserRole(
				systemRole.getName() + " (uafgrænset)",
				"Ubegrænset adgang via " + systemRole.getName(),
				systemRole,
				toIdentifier(systemRole.getIdentifier(), "unconstrained"),
				List.of()
			);

			List<ConstraintTypeSupport> supportedConstraints = systemRole.getSupportedConstraintTypes();
			if (supportedConstraints != null && !supportedConstraints.isEmpty()) {
				for (ConstraintTypeSupport support : supportedConstraints) {
					List<SystemRoleAssignmentConstraintValue> constraintValues = buildConstraintValues(support, itSystemId);
					String constraintName = support.getConstraintType().getName();
					createUserRole(
						systemRole.getName() + " (" + constraintName + ")",
						systemRole.getName() + " afgrænset til " + constraintName,
						systemRole,
						toIdentifier(systemRole.getIdentifier(), "constrained-" + support.getConstraintType().getEntityId()),
						constraintValues
					);
				}
			}
		}

		for (UserRoleDef def : USER_ROLES) {
			Long systemRoleId = allSystemRoleIds.get(def.systemRoleIdentifier());
			if (systemRoleId == null) {
				log.warn("Skipping custom user role '{}' — system role '{}' not found", def.identifier(), def.systemRoleIdentifier());
				continue;
			}
			SystemRole systemRole = systemRoleService.getById(systemRoleId);
			List<SystemRoleAssignmentConstraintValue> constraintValues = def.constraints().stream()
				.map(cvDef -> {
					ConstraintType constraintType = constraintTypeService.getByEntityId(cvDef.constraintEntityId());
					SystemRoleAssignmentConstraintValue cv = new SystemRoleAssignmentConstraintValue();
					cv.setConstraintType(constraintType);
					cv.setConstraintValueType(cvDef.valueType());
					cv.setConstraintValue(cvDef.value());
					cv.setPostponed(false);
					return cv;
				})
				.toList();
			createUserRole(def.name(), def.description(), systemRole, def.identifier(), constraintValues);
		}
	}

	private UserRole createUserRole(String name, String description, SystemRole systemRole,
									String identifier, List<SystemRoleAssignmentConstraintValue> constraintValues) {
		UserRole userRole = new UserRole();
		userRole.setName(name);
		userRole.setDescription(description);
		userRole.setIdentifier(identifier);
		userRole.setItSystem(systemRole.getItSystem());
		userRole.setSystemRoleAssignments(new ArrayList<>());
		userRole.setRoleAssignmentAttestationByAttestationResponsible(true);

		SystemRoleAssignment sra = new SystemRoleAssignment();
		sra.setSystemRole(systemRole);
		sra.setUserRole(userRole);
		sra.setAssignedByName("Development Bootstrapper");
		sra.setAssignedByUserId("dev-bootstrapper");
		sra.setAssignedTimestamp(new Date());
		sra.setConstraintValues(new ArrayList<>(constraintValues));
		constraintValues.forEach(cv -> cv.setSystemRoleAssignment(sra));

		userRole.getSystemRoleAssignments().add(sra);
		return userRoleService.save(userRole);
	}

	private List<SystemRoleAssignmentConstraintValue> buildConstraintValues(ConstraintTypeSupport support, long itSystemId) {
		String entityId = support.getConstraintType().getEntityId();

		// OU constraint uses INHERITED so it follows the user's org unit membership
		if (Constants.OU_CONSTRAINT_ENTITY_ID.equals(entityId)) {
			SystemRoleAssignmentConstraintValue cv = new SystemRoleAssignmentConstraintValue();
			cv.setConstraintType(support.getConstraintType());
			cv.setConstraintValueType(ConstraintValueType.INHERITED);
			cv.setPostponed(false);
			return List.of(cv);
		}

		// IT system constraint uses the DB id of the system role's own IT system
		if (Constants.KOMBIT_ITSYSTEM_CONSTRAINT_ENTITY_ID.equals(entityId)) {
			SystemRoleAssignmentConstraintValue cv = new SystemRoleAssignmentConstraintValue();
			cv.setConstraintType(support.getConstraintType());
			cv.setConstraintValueType(ConstraintValueType.VALUE);
			cv.setConstraintValue(Long.toString(itSystemId));
			cv.setPostponed(false);
			return List.of(cv);
		}

		String sampleValue = CONSTRAINT_SAMPLE_VALUES.get(entityId);
		if (sampleValue != null) {
			SystemRoleAssignmentConstraintValue cv = new SystemRoleAssignmentConstraintValue();
			cv.setConstraintType(support.getConstraintType());
			cv.setConstraintValueType(ConstraintValueType.VALUE);
			cv.setConstraintValue(sampleValue);
			cv.setPostponed(false);
			return List.of(cv);
		}

		return List.of();
	}

	/**
	 * Produces a short deterministic identifier from a role identifier + suffix.
	 * Keeps it within 128 chars (UserRole.identifier column limit).
	 */
	private String toIdentifier(String roleIdentifier, String suffix) {
		String candidate = roleIdentifier + "-" + suffix;
		if (candidate.length() > 128) {
			candidate = roleIdentifier.substring(0, Math.max(0, 127 - suffix.length())) + "-" + suffix;
		}
		return candidate;
	}

}
