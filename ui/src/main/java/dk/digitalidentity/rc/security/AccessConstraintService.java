package dk.digitalidentity.rc.security;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import dk.digitalidentity.rc.dao.model.PostponedConstraint;
import dk.digitalidentity.rc.dao.model.SystemRole;
import dk.digitalidentity.rc.dao.model.UserUserRoleAssignment;
import dk.digitalidentity.rc.service.model.RoleAssignedToUserDTO;
import dk.digitalidentity.rc.service.model.UserWithRole;
import org.springframework.beans.factory.annotation.Autowired;
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

@Service
public class AccessConstraintService {

	@Autowired
	private UserService userService;

	@Autowired
	private OrgUnitService orgUnitService;

	@Autowired
	private ItSystemService itSystemService;

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
			return new ArrayList<String>(); // empty list, full restriction
		}

		List<ItSystem> itSystems = itSystemService.findByIdentifier(Constants.ROLE_CATALOGUE_IDENTIFIER);

		// get all roles, and start analyzing the data
		List<UserRole> allRoleCatalogueRoles = userService.getAllUserRoles(user, itSystems);
		Set<String> resultSet = new HashSet<String>();

		for (UserRole role : allRoleCatalogueRoles) {
			for (SystemRoleAssignment systemRoleAssignment : role.getSystemRoleAssignments()) {
				boolean readOnlyRole = false;

				switch (systemRoleAssignment.getSystemRole().getIdentifier()) {
					case Constants.ROLE_READ_ACCESS_ID:
						readOnlyRole = true;
						// fall-through
					case Constants.ROLE_KLE_ADMINISTRATOR_ID:
					case Constants.ROLE_ASSIGNER_ID:
					case Constants.ROLE_REQUESTAUTHORIZED:
						// these can be constrained, so let's examine them further
						break;
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
								getUuidsRecursive(resultSet, pos.getOrgUnit());
							}

							break;
						case VALUE:
							for (String uuid : constraintValue.getConstraintValue().split(",")) {
								resultSet.add(uuid);
							}

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
		return (filterUsersUserCanAccess(Collections.singletonList(user), writeAccessRequired).size() > 0);
	}

	public boolean isAssignmentAllowed(User user, UserRole userRole) {
		if (!isUserAccessable(user, true)) {
			return false;
		}

		return (filterUserRolesUserCanAssign(Collections.singletonList(userRole)).size() > 0);
	}

	public boolean isAssignmentAllowed(User user, RoleGroup roleGroup) {
		if (!isUserAccessable(user, true)) {
			return false;
		}

		return (filterRoleGroupsUserCanAssign(Collections.singletonList(roleGroup)).size() > 0);
	}

	public boolean isAssignmentAllowed(OrgUnit ou) {
		if (!isOUAccessable(ou,false)) {
			return false;
		}

		return true;
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
		if (!isOUAccessable(ou, true)) {
			return false;
		}

		return true;
	}

	public boolean isKleAssignmentAllowed(User user) {
		if (!isUserAccessable(user, true)) {
			return false;
		}

		return true;
	}

	public boolean isAssignmentAllowed(OrgUnit ou, UserRole userRole) {
		if (!isOUAccessable(ou, true)) {
			return false;
		}

		return (filterUserRolesUserCanAssign(Collections.singletonList(userRole)).size() > 0);
	}

	public boolean isAssignmentAllowed(OrgUnit ou, RoleGroup roleGroup) {
		if (!isOUAccessable(ou, true)) {
			return false;
		}

		return (filterRoleGroupsUserCanAssign(Collections.singletonList(roleGroup)).size() > 0);
	}

	public List<User> filterUsersUserCanAccess(List<User> users, boolean writeAccessRequired) {
		List<String> ous = getConstrainedOrgUnits(writeAccessRequired);
		if (ous == null) {
			return users; // no filtering
		}

		List<User> result = new ArrayList<>();

		if (ous.size() == 0) {
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
			return new ArrayList<Long>(); // empty list, full restriction
		}

		List<ItSystem> itSystems = itSystemService.findByIdentifier(Constants.ROLE_CATALOGUE_IDENTIFIER);

		List<UserRole> allRoles = userService.getAllUserRoles(user, itSystems);
		Set<Long> resultSet = new HashSet<Long>();

		for (UserRole role : allRoles) {
			for (SystemRoleAssignment systemRoleAssignment : role.getSystemRoleAssignments()) {

				// if the user is Administrator - return full list (i.e. null)
				if (systemRoleAssignment.getSystemRole().getIdentifier().equals(Constants.ROLE_ADMINISTRATOR_ID)) {
					return null;
				}

				// if the user is a RoleAssigner, it depends on constraints
				if (systemRoleAssignment.getSystemRole().getIdentifier().equals(Constants.ROLE_ASSIGNER_ID)) {

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

		if (itSystems.size() == 0) {
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

		if (itSystems.size() == 0) {
			return new ArrayList<>(); // no access
		}

		List<RoleGroup> result = new ArrayList<>();
		for (RoleGroup roleGroup : roleGroups) {
			List<UserRole> userRoles = roleGroup.getUserRoleAssignments().stream().map(ura -> ura.getUserRole()).collect(Collectors.toList());

			long filteredSize = userRoles.stream()
					 .filter(r -> itSystems.contains(r.getItSystem().getId()))
					 .collect(Collectors.toList())
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
				UserUserRoleAssignment userUserRoleAssignment = user.getUserRoleAssignments().stream().filter(ura->ura.getId() == assignment.getAssignmentId()).findAny().orElse(null);
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
							getUuidsRecursive(ouUuids, pos.getOrgUnit());
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
				UserUserRoleAssignment userUserRoleAssignment = user.getUserRoleAssignments().stream().filter(ura->ura.getId() == assignment.getAssignmentId()).findAny().orElse(null);
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

	private void getUuidsRecursive(Set<String> uuids, OrgUnit orgUnit) {
		uuids.add(orgUnit.getUuid());

		for (OrgUnit child : orgUnit.getChildren()) {
			getUuidsRecursive(uuids, child);
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

		if (ous == null || ous.contains(orgUnit.getUuid())) {
			return true;
		}

		return false;
	}
}
