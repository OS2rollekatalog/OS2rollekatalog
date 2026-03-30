package dk.digitalidentity.rc.security;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import dk.digitalidentity.rc.dao.model.PostponedConstraint;
import dk.digitalidentity.rc.dao.model.RoleGroupUserRoleAssignment;
import dk.digitalidentity.rc.security.permission.Permission;
import dk.digitalidentity.rc.security.permission.PermissionConstraint;
import dk.digitalidentity.rc.security.permission.Section;
import dk.digitalidentity.rc.service.PostponedConstraintService;
import dk.digitalidentity.rc.service.assignment.AssignmentService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import dk.digitalidentity.rc.dao.model.SystemRole;
import dk.digitalidentity.rc.dao.model.UserUserRoleAssignment;
import dk.digitalidentity.rc.service.model.RoleAssignedToUserDTO;
import dk.digitalidentity.rc.service.model.UserWithRole;
import org.springframework.stereotype.Service;

import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignment;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignmentConstraintValue;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.enums.ConstraintValueType;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.rc.util.OrganisationConstraintUtil;

@RequiredArgsConstructor
@Service
public class AccessConstraintService {

	private final UserService userService;
	private final OrgUnitService orgUnitService;
	private final ItSystemService itSystemService;
	private final OrganisationConstraintUtil organisationConstraintUtil;
	private final PostponedConstraintService postponedConstraintService;
	private final AssignmentService assignmentService;

	// TODO - refactoring target - we should have a central way to check constraints of ANY type, that we can use everywhere we need to check constraints

	/***
	 * Returns a list of OrgUnits that this is constrained to.
	 * null -> no constraints
	 * empty list -> fully constrained, not allowed access
	 */
	public List<String> getConstrainedOrgUnits(boolean writeAccessRequired) {

		// system users and administrators are never restricted
		if (SecurityUtil.getRoles().contains(Constants.ROLE_SYSTEM) || SecurityUtil.getRoles().contains(Constants.ROLE_ADMINISTRATOR)) {
			return null;
		}

		// the logged in user must exists in the database, otherwise no access
		User user = userService.getByUserId(SecurityUtil.getUserId());
		if (user == null) {
			return new ArrayList<>(); // empty list, full restriction
		}

		List<ItSystem> itSystems = itSystemService.findByIdentifier(Constants.ROLE_CATALOGUE_IDENTIFIER);

		// get all roles, and start analyzing the data
		List<UserRole> allRoleCatalogueRoles = new ArrayList<>(assignmentService.getUserRolesByUserAndSystems(user, itSystems));
		Set<String> resultSet = new HashSet<>();

		for (UserRole role : allRoleCatalogueRoles) {
			for (SystemRoleAssignment systemRoleAssignment : role.getSystemRoleAssignments()) {
				boolean readOnlyRole = false;
				switch (systemRoleAssignment.getSystemRole().getIdentifier()) {
					case Constants.ROLE_READ_ACCESS_ID:
					case Constants.ROLE_OU_READ_ID:
						readOnlyRole = true;
						// fall-through
					case Constants.ROLE_KLE_ADMINISTRATOR_ID:
					case Constants.ROLE_USER_ASSIGNER_ID, Constants.ROLE_OU_ASSIGNER_ID, Constants.ROLE_OU_UPDATE_ID:
						// these can be constrained, so let's examine them further
						break;
					case Constants.ROLE_GLOBAL_ASSIGNER_ID:
						// Global assigner cannot be constrained
						return null;
					default:
						// any other role cannot be constraint, so we skip it
						continue;
				}

				// skip readOnly roles when write access is required
				if (writeAccessRequired && readOnlyRole) {
					continue;
				}

				// only look at constraints of the relevant type
				List<SystemRoleAssignmentConstraintValue> filteredConstraints = null;
				if (systemRoleAssignment.getConstraintValues() != null) {
					filteredConstraints = systemRoleAssignment
						.getConstraintValues()
						.stream()
						.filter(c -> c.getConstraintType().getEntityId().equals(Constants.INTERNAL_ORGUNIT_CONSTRAINT_ENTITY_ID))
						.toList();
				}

				// unconstrained roles gives full access
				if (filteredConstraints == null || filteredConstraints.isEmpty()) {
					return null;
				}

				// if constrained on OrgUnits, sum up (could be assigned the role multiple times)
				for (SystemRoleAssignmentConstraintValue constraintValue : filteredConstraints) {
					ConstraintValueType constraintValueType = constraintValue.getConstraintValueType();

					switch (constraintValueType) {
						case INHERITED:
							for (Position pos : user.getPositions()) {
								resultSet.add(pos.getOrgUnit().getUuid());
							}

							break;
						case EXTENDED_INHERITED:
							for (Position pos : user.getPositions()) {
								getUuidsRecursive(pos.getOrgUnit(), resultSet);
							}

							break;
						case SELECTED_INHERITED:
						case VALUE:
							String value = constraintValue.getConstraintValueType().equals(ConstraintValueType.VALUE) ? constraintValue.getConstraintValue() : String.join(",", organisationConstraintUtil.getOrganisationConstraintUuids(constraintValue.getConstraintValue()));
							resultSet.addAll(Arrays.asList(value.split(",")));
							break;
						default:
							break;
					}
				}
			}
		}

		// if (and only if) this is manager without read-access, we will add all the OUs for that manager
		if (SecurityUtil.isManagerWithoutReadAccess()) {
			List<OrgUnit> orgUnits = orgUnitService.getByManager(); // uses the currently logged in user internally

			for (OrgUnit orgUnit : orgUnits) {
				resultSet.add(orgUnit.getUuid());
			}
		}
		return new ArrayList<>(resultSet);
	}

	// do not use from inside large loop, as the filterUsersUserCanEdit is expensive
	public boolean isUserAccessable(User user, boolean writeAccessRequired) {
		return !filterUsersUserCanAccess(Collections.singletonList(user), !writeAccessRequired).isEmpty();
	}

	public boolean isAssignmentAllowed(User user, UserRole userRole) {
		if (!isUserAccessable(user, true)) {
			return false;
		}

		return !filterUserRolesUserCanAssign(Collections.singletonList(userRole)).isEmpty();
	}

	public boolean isAssignmentAllowed(User user, RoleGroup roleGroup) {
		if (!isUserAccessable(user, true)) {
			return false;
		}

		return !filterRoleGroupsUserCanAssign(Collections.singletonList(roleGroup)).isEmpty();
	}

	public boolean isAssignmentAllowed(OrgUnit ou) {
		return isOUAccessable(ou, false);
	}

	public boolean isUserRoleAssignmentAllowed(final OrgUnit ou, final Long ouRoleAssignmentId) {
		return ou.getUserRoleAssignments()
			.stream().filter(ura -> ura.getId() == ouRoleAssignmentId).findAny()
			.map(a -> isAssignmentAllowed(ou, a.getUserRole()))
			.orElseThrow(() -> new IllegalArgumentException("Could not lookup ouRoleAssignment " + ouRoleAssignmentId));
	}

	public boolean isUserRoleGroupAssignmentAllowed(final OrgUnit ou, final Long ouRoleGroupAssignmentId) {
		return ou.getRoleGroupAssignments()
			.stream().filter(ura -> ura.getId() == ouRoleGroupAssignmentId).findAny()
			.map(a -> isAssignmentAllowed(ou, a.getRoleGroup()))
			.orElseThrow(() -> new IllegalArgumentException("Could not lookup ouRoleGroupAssignment " + ouRoleGroupAssignmentId));
	}

	public boolean isKleAssignmentAllowed(OrgUnit ou) {
		return isOUAccessable(ou, true);
	}

	public boolean isKleAssignmentAllowed(User user) {
		return isUserAccessable(user, true);
	}

	public boolean isAssignmentAllowed(OrgUnit ou, UserRole userRole) {
		if (!isOUAccessable(ou, true)) {
			return false;
		}

		return (!filterUserRolesUserCanAssign(Collections.singletonList(userRole)).isEmpty());
	}

	public boolean isAssignmentAllowed(OrgUnit ou, RoleGroup roleGroup) {
		if (!isOUAccessable(ou, true)) {
			return false;
		}

		return (!filterRoleGroupsUserCanAssign(Collections.singletonList(roleGroup)).isEmpty());
	}

	public List<User> filterUsersUserCanAccess(List<User> users, boolean writeAccessRequired) {
		List<String> ous = getConstrainedOrgUnits(writeAccessRequired);
		if (ous == null) {
			return users; // no filtering
		}

		List<User> result = new ArrayList<>();

		if (ous.isEmpty()) {
			return result;
		}

		for (User user : users) {
			List<OrgUnit> userOrgUnits = getUserOrgUnits(user);

			for (OrgUnit userOrgUnit : userOrgUnits) {
				if (ous.contains(userOrgUnit.getUuid())) {
					result.add(user);
					break;
				}
			}
		}

		return result;
	}

	// null ==> no restriction on OrgUnits
	// empty array => full restriction (i.e. no OrgUnits)
	// list with elements => restricted to those OrgUnits
	public List<Long> itSystemsUserCanEdit() {

		// system user is never restricted
		if (SecurityUtil.getRoles().contains(Constants.ROLE_SYSTEM)) {
			return null;
		}

		User user = userService.getByUserId(SecurityUtil.getUserId());
		if (user == null) {
			return new ArrayList<>(); // empty list, full restriction
		}

		List<ItSystem> itSystems = itSystemService.findByIdentifier(Constants.ROLE_CATALOGUE_IDENTIFIER);

		List<UserRole> allRoles = new ArrayList<>(assignmentService.getUserRolesByUserAndSystems(user, itSystems));
		Set<Long> resultSet = new HashSet<>();

		for (UserRole role : allRoles) {
			for (SystemRoleAssignment systemRoleAssignment : role.getSystemRoleAssignments()) {

				// if the user is Administrator - return full list (i.e. null)
				if (systemRoleAssignment.getSystemRole().getIdentifier().equals(Constants.ROLE_ADMINISTRATOR_ID)) {
					return null;
				}

				// if the user is a RoleAssigner, it depends on constraints
				if (systemRoleAssignment.getSystemRole().getIdentifier().equals(Constants.ROLE_USER_ASSIGNER_ID)
					|| systemRoleAssignment.getSystemRole().getIdentifier().equals(Constants.ROLE_OU_ASSIGNER_ID)) {

					// if not constraint, grant full access
					if (systemRoleAssignment.getConstraintValues() == null) {
						return null;
					}

					// only look at constraints of the relevant type
					List<SystemRoleAssignmentConstraintValue> filteredConstraints = systemRoleAssignment
						.getConstraintValues()
						.stream()
						.filter(c -> c.getConstraintType().getEntityId().equals(Constants.INTERNAL_ITSYSTEM_CONSTRAINT_ENTITY_ID))
						.filter(c -> StringUtils.isNotBlank(c.getConstraintValue()))
						.toList();

					// at least one RoleAssigner role with no constraints on itSystems, gives full access
					if (filteredConstraints.isEmpty()) {
						return null;
					}

					// if constrained on itSystems, sum up (could be assigned the role multiple times)
					for (SystemRoleAssignmentConstraintValue constraintValue : filteredConstraints) {
						for (String val : constraintValue.getConstraintValue().split(",")) {
							if (StringUtils.isNumeric(val)) {
								resultSet.add(Long.parseLong(val));
							}
						}
					}
				}
			}
		}

		return new ArrayList<>(resultSet);
	}

	public List<UserRole> filterUserRolesUserCanAssign(List<UserRole> roles) {
		List<Long> itSystems = itSystemsUserCanEdit();
		if (itSystems == null) {
			return roles; // no filtering
		}

		if (itSystems.isEmpty()) {
			return new ArrayList<>(); // no access
		}

		return roles.stream()
			.filter(r -> itSystems.contains(r.getItSystem().getId()))
			.collect(Collectors.toList());
	}

	public List<RoleGroup> filterRoleGroupsUserCanAssign(List<RoleGroup> roleGroups) {
		List<Long> itSystems = itSystemsUserCanEdit();
		if (itSystems == null) {
			return roleGroups; // no filtering
		}

		if (itSystems.isEmpty()) {
			return new ArrayList<>(); // no access
		}

		List<RoleGroup> result = new ArrayList<>();
		for (RoleGroup roleGroup : roleGroups) {
			List<UserRole> userRoles = roleGroup.getUserRoleAssignments().stream().map(RoleGroupUserRoleAssignment::getUserRole).toList();

			long filteredSize = userRoles.stream()
				.filter(r -> itSystems.contains(r.getItSystem().getId()))
				.toList()
				.size();

			// if all userRoles are allowed, allow the roleGroup
			if (filteredSize == userRoles.size()) {
				result.add(roleGroup);
			}
		}

		return result;
	}

	public Map<String, List<String>> findOUAndITSystemConstraintsForSystemRoleInUserRole(UserRole userRole, SystemRole systemRole, UserWithRole usersWithRole) {
		Map<String, List<String>> result = new HashMap<>();
		User user = usersWithRole.getUser();
		RoleAssignedToUserDTO assignment = usersWithRole.getAssignment();

		SystemRoleAssignment systemRoleAssignment = userRole.getSystemRoleAssignments().stream().filter(sra -> sra.getSystemRole().getId() == systemRole.getId()).findAny().orElse(null);
		if (systemRoleAssignment == null) {
			return null;
		}

		// only look at constraints of the relevant type
		List<SystemRoleAssignmentConstraintValue> ouConstraints = new ArrayList<>();
		List<SystemRoleAssignmentConstraintValue> itSystemConstraints = new ArrayList<>();
		if (systemRoleAssignment.getConstraintValues() != null) {
			ouConstraints = systemRoleAssignment
				.getConstraintValues()
				.stream()
				.filter(c -> c.getConstraintType().getEntityId().equals(Constants.INTERNAL_ORGUNIT_CONSTRAINT_ENTITY_ID))
				.collect(Collectors.toList());
			itSystemConstraints = systemRoleAssignment
				.getConstraintValues()
				.stream()
				.filter(c -> c.getConstraintType().getEntityId().equals(Constants.INTERNAL_ITSYSTEM_CONSTRAINT_ENTITY_ID))
				.collect(Collectors.toList());
		}

		// if constrained on OrgUnits, sum up (could be assigned the role multiple times)
		Set<String> ouUuids = new HashSet<>();
		for (SystemRoleAssignmentConstraintValue constraintValue : ouConstraints) {
			ConstraintValueType constraintValueType = constraintValue.getConstraintValueType();

			if (constraintValue.isPostponed()) {
				UserUserRoleAssignment userUserRoleAssignment = user.getUserRoleAssignments().stream().filter(ura -> ura.getId() == assignment.getAssignmentId()).findAny().orElse(null);
				PostponedConstraint postponedConstraint = userUserRoleAssignment.getPostponedConstraints().stream().filter(p -> p.getSystemRole().getId() == systemRoleAssignment.getSystemRole().getId() && p.getConstraintType().getUuid().equals(constraintValue.getConstraintType().getUuid())).findAny().orElse(null);

				if (postponedConstraint != null) {
					String[] uuids = postponedConstraint.getValue().split(",");
					ouUuids.addAll(Arrays.asList(uuids));
				}
			} else {
				switch (constraintValueType) {
					case INHERITED:
						for (Position pos : user.getPositions()) {
							ouUuids.add(pos.getOrgUnit().getUuid());
						}

						break;
					case EXTENDED_INHERITED:
						for (Position pos : user.getPositions()) {
							getUuidsRecursive(pos.getOrgUnit(), ouUuids);
						}

						break;
					case VALUE:
						for (String uuid : constraintValue.getConstraintValue().split(",")) {
							ouUuids.add(uuid);
						}

						break;
					default:
						break;
				}
			}
		}

		Set<Long> itSystemIds = new HashSet<>();
		for (SystemRoleAssignmentConstraintValue constraintValue : itSystemConstraints) {

			if (constraintValue.isPostponed()) {
				UserUserRoleAssignment userUserRoleAssignment = user.getUserRoleAssignments().stream().filter(ura -> ura.getId() == assignment.getAssignmentId()).findAny().orElse(null);
				PostponedConstraint postponedConstraint = userUserRoleAssignment.getPostponedConstraints().stream().filter(p -> p.getSystemRole().getId() == systemRoleAssignment.getSystemRole().getId() && p.getConstraintType().getUuid().equals(constraintValue.getConstraintType().getUuid())).findAny().orElse(null);

				if (postponedConstraint != null) {
					String[] ids = postponedConstraint.getValue().split(",");
					for (String id : ids) {
						itSystemIds.add(Long.parseLong(id));
					}
				}
			} else {
				String[] ids = constraintValue.getConstraintValue().split(",");
				for (String id : ids) {
					itSystemIds.add(Long.parseLong(id));
				}
			}
		}

		if (!ouUuids.isEmpty()) {
			result.put("Enheder", new ArrayList<>());
			for (String ouUuid : ouUuids) {
				OrgUnit ou = orgUnitService.getByUuid(ouUuid);
				if (ou != null) {
					result.get("Enheder").add(ou.getName());
				}
			}
		}

		if (!itSystemIds.isEmpty()) {
			result.put("It-systemer", new ArrayList<>());
			for (Long itSystemId : itSystemIds) {
				ItSystem itSystem = itSystemService.getById(itSystemId);
				if (itSystem != null) {
					result.get("It-systemer").add(itSystem.getName());
				}
			}
		}

		return result;
	}

	private void getUuidsRecursive(OrgUnit orgUnit, Set<String> uuids) {
		uuids.add(orgUnit.getUuid());

		for (OrgUnit child : orgUnit.getChildren()) {
			getUuidsRecursive(child, uuids);
		}
	}

	private List<OrgUnit> getUserOrgUnits(User user) {
		List<OrgUnit> orgUnits = new ArrayList<>();
		for (Position p : user.getPositions()) {
			orgUnits.add(p.getOrgUnit());
		}

		return orgUnits;
	}

	// do not use this method from a loop over all orgUnits, as the orgUnitsUserCanEdit call is expensive
	private boolean isOUAccessable(OrgUnit orgUnit, boolean writeAccessRequired) {
		List<String> ous = getConstrainedOrgUnits(writeAccessRequired);

		return ous == null || ous.contains(orgUnit.getUuid());
	}

	public <T> Set<T> getIdsForConstraints(SystemRoleAssignment systemRoleAssignment, Set<SystemRoleAssignmentConstraintValue> constraints, Function<String, T> idParsingFunction, User user, String constraintEntityId) {
		return constraints.stream()
			.flatMap(cv ->
				switch (cv.getConstraintValueType()) {
					// depending on the constraint type, extract the constrained ids to a list
					case INHERITED -> getIdsForConstraintsWithType_INHERITED(user).stream();
					case EXTENDED_INHERITED -> getIdsForConstraintsWithType_INHERITED_EXTENDED(user).stream();
					case SELECTED_INHERITED ->
						organisationConstraintUtil.getOrganisationConstraintUuids(cv.getConstraintValue()).stream();
					case LEVEL_1, LEVEL_2, LEVEL_3, LEVEL_4, LEVEL_5, LEVEL_6 ->
						Arrays.stream(userService.getOrganisationConstraint(user, cv.getConstraintValueType()).split(","));
					case POSTPONED ->
						getIdsForConstraintsWithType_POSTPONED(user, systemRoleAssignment, constraintEntityId).stream();
					case VALUE -> {
						if (cv.getConstraintValue() == null || cv.getConstraintValue().isEmpty()) {
							yield null;
						}
						yield Arrays.stream(cv.getConstraintValue().split(","));
					}
					default -> null; // default is fully constrained
				})
			// trim and parse strings
			.map(String::trim)
			.map(idParsingFunction)
			.filter(Objects::nonNull)
			.collect(Collectors.toSet());
	}

	private Set<String> getIdsForConstraintsWithType_INHERITED(User user) {
		// Inherited constraints is always the uuid of the orgunits the user is in
		return user.getPositions().stream()
			.map(p -> p.getOrgUnit().getUuid())
			.collect(Collectors.toSet());
	}

	private Set<String> getIdsForConstraintsWithType_INHERITED_EXTENDED(User user) {
		// Extended Inherited constraints is the uuids of the recursive ous the user is a part of
		Set<String> uuids = new HashSet<>();
		for (Position pos : user.getPositions()) {
			getUuidsRecursive(pos.getOrgUnit(), uuids);
		}
		return uuids;
	}

	private Set<String> getIdsForConstraintsWithType_POSTPONED(User user, SystemRoleAssignment systemRoleAssignment, String constraintEntityId) {
		List<PostponedConstraint> postponedConstraints = postponedConstraintService.getPostPonedConstraintValues(user, systemRoleAssignment);
		return postponedConstraints.stream()
			.filter(pc -> pc.getConstraintType().getEntityId().equals(constraintEntityId)) // only get for the relevant constraint type
			.map(PostponedConstraint::getValue)
			.collect(Collectors.toSet());
	}

	public Long parseLongOrNull(String value) {
		try {
			return Long.parseLong(value);
		} catch (NumberFormatException e) {
			// Non-valid numbers are ignored for now. Is there a better way to handle them?
			return null;
		}
	}

	/**
	 * Constructs a Permission map for a user, based on their permission-granting system roles in Rolecatalogue.
	 * @param user target user
	 * @param rolecatalogue rolecatalogue IT system
	 * @return a Map of sections, containing maps of Permissions and their constraints
	 */
	public Map<Section, Map<Permission, PermissionConstraint>> constructUserPermissions(User user, ItSystem rolecatalogue) {
		EnumMap<Section, Map<Permission, PermissionConstraint>> permissionByEntityMap = new EnumMap<>(Section.class);

		// Find all userroles in RC for the current user, no matter how they are assigned
		List<UserRole> userRoles = new ArrayList<>(assignmentService.getUserRolesByUserAndSystems(user, List.of(rolecatalogue)));
		Set<SystemRoleAssignment> systemRoleAssignments = userRoles.stream().flatMap(ur -> ur.getSystemRoleAssignments().stream()).collect(Collectors.toSet());

		Map<String, List<PostponedConstraint>> allPostponedConstraints = postponedConstraintService.findAllForUserAndRoleCatalogue(user).stream()
			.collect(Collectors.groupingBy(pc -> pc.getSystemRole().getIdentifier()));

		for (SystemRoleAssignment assignment : systemRoleAssignments) {
			String systemRoleIdentifier = assignment.getSystemRole().getIdentifier();

			// get default permissions for this role
			Map<Section, Set<Permission>> defaultPermissionsForRole = SecurityUtil.getPermissionsForRoles(Set.of(systemRoleIdentifier));

			// Get all constraints for this role, filtered by RoleCatalogue relevant constraints
			PermissionConstraint newConstraint = constructPermissionConstraintForAssignment(assignment, user);

			PermissionConstraint combinedConstraints;
			if (!allPostponedConstraints.isEmpty() && allPostponedConstraints.containsKey(systemRoleIdentifier)) {
				PermissionConstraint postponedConstraint = constructPostponedPermissionConstraints(allPostponedConstraints, systemRoleIdentifier);
				combinedConstraints = newConstraint.merge(postponedConstraint);
			} else {
				combinedConstraints = newConstraint;
			}

			// For every default permission for this system role, construct the constraints and save them in the map
			for (Map.Entry<Section, Set<Permission>> entry : defaultPermissionsForRole.entrySet()) {
				Map<Permission, PermissionConstraint> existingPermissionConstraints = permissionByEntityMap.computeIfAbsent(entry.getKey(), _ -> new EnumMap<>(Permission.class));

				// add the new constraint to the existing constraints in the permission map
				addPermissionConstraintsToMap(existingPermissionConstraints, entry.getValue(), combinedConstraints);
			}
		}

		return permissionByEntityMap;
	}

	private PermissionConstraint constructPostponedPermissionConstraints(Map<String, List<PostponedConstraint>> postponedConstraintsBySystemRoleIdentifier, String systemRoleIdentifier) {
		List<PostponedConstraint> postponedConstraints = postponedConstraintsBySystemRoleIdentifier.getOrDefault(systemRoleIdentifier, null);
		if (postponedConstraints == null) {
			return new PermissionConstraint(null, null);
		}

		Set<PostponedConstraint> postponedITSystemConstraint = postponedConstraints.stream().filter(p -> p.getConstraintType().getEntityId().equals(Constants.INTERNAL_ITSYSTEM_CONSTRAINT_ENTITY_ID)).collect(Collectors.toSet());
		Set<Long> postponedITSystemConstraintIds = postponedITSystemConstraint.isEmpty() ? null : postponedITSystemConstraint.stream()
			.map(PostponedConstraint::getValue)
			.flatMap(v -> Arrays.stream(v.split(",")))
			.map(String::trim)
			.filter(StringUtils::isNotBlank)
			.map(this::parseLongOrNull)
			.filter(Objects::nonNull) // note that this silently ignores malformed values
			.collect(Collectors.toSet());

		List<PostponedConstraint> postPonedOuConstraints = postponedConstraints.stream().filter(p -> p.getConstraintType().getEntityId().equals(Constants.INTERNAL_ORGUNIT_CONSTRAINT_ENTITY_ID)).toList();
		Set<String> postponedOuConstraintUuids = postPonedOuConstraints.isEmpty() ? null : postPonedOuConstraints.stream()
			.map(PostponedConstraint::getValue)
			.flatMap(v -> Arrays.stream(v.split(",")))
			.map(String::trim)
			.filter(StringUtils::isNotBlank)
			.collect(Collectors.toSet());

		return new PermissionConstraint(postponedITSystemConstraintIds, postponedOuConstraintUuids);
	}

	private void addPermissionConstraintsToMap(Map<Permission, PermissionConstraint> existingPermissionConstraints, Set<Permission> permissions, PermissionConstraint newConstraint) {
		if (permissions == null || permissions.isEmpty()) {
			return;
		}

		for (Permission permission : permissions) {
			PermissionConstraint existingConstraint = existingPermissionConstraints.get(permission);
			PermissionConstraint updatedConstraint;

			if (existingConstraint == null) {
				updatedConstraint = newConstraint.copy();
			} else {
				updatedConstraint = existingConstraint.merge(newConstraint);
			}

			existingPermissionConstraints.put(permission, updatedConstraint);
		}
	}

	private PermissionConstraint constructPermissionConstraintForAssignment(SystemRoleAssignment assignment, User user) {
		Map<String, Set<SystemRoleAssignmentConstraintValue>> constraints = assignment.getConstraintValues().stream()
			.filter(cv -> // filter only those that are relevant for role catalogue
				Objects.equals(cv.getConstraintType().getEntityId(), Constants.INTERNAL_ITSYSTEM_CONSTRAINT_ENTITY_ID)
					|| Objects.equals(cv.getConstraintType().getEntityId(), Constants.INTERNAL_ORGUNIT_CONSTRAINT_ENTITY_ID))
			.collect(Collectors.groupingBy(cv -> cv.getConstraintType().getEntityId(), Collectors.toSet()));

		// if there are no constraints of a given type, it means full access and takes precedence
		// over any constraints set by other system role assignments
		Set<Long> constrainedITSystemIds = null;
		if (constraints.get(Constants.INTERNAL_ITSYSTEM_CONSTRAINT_ENTITY_ID) != null) {
			constrainedITSystemIds = getIdsForConstraints(
				assignment,
				constraints.get(Constants.INTERNAL_ITSYSTEM_CONSTRAINT_ENTITY_ID),
				this::parseLongOrNull,
				user,
				Constants.INTERNAL_ITSYSTEM_CONSTRAINT_ENTITY_ID
			);
		}
		Set<String> constrainedOrgunitUUids = null;
		if (constraints.get(Constants.INTERNAL_ORGUNIT_CONSTRAINT_ENTITY_ID) != null) {
			constrainedOrgunitUUids = getIdsForConstraints(
				assignment,
				constraints.get(Constants.INTERNAL_ORGUNIT_CONSTRAINT_ENTITY_ID),
				s -> s,
				user,
				Constants.INTERNAL_ORGUNIT_CONSTRAINT_ENTITY_ID
			);
		}

		// add a permissionconstraint with those lists to the relevant permission and section
		return new PermissionConstraint(constrainedITSystemIds, constrainedOrgunitUUids);
	}
}
