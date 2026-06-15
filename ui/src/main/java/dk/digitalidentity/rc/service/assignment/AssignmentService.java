package dk.digitalidentity.rc.service.assignment;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.assignment.CurrentAssignment;
import dk.digitalidentity.rc.dao.model.assignment.CurrentAssignmentSmallProjection;
import dk.digitalidentity.rc.dao.model.assignment.CurrentExceptedAssignment;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.assignment.model.AssignmentType;
import dk.digitalidentity.rc.service.model.AssignedThrough;
import dk.digitalidentity.rc.service.model.RoleAssignedToUserDTO;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service
public class AssignmentService {
	private final CurrentAssignmentService currentAssignmentService;
	private final CurrentExceptedAssignmentService currentExceptedAssignmentService;
	private final ItSystemService itSystemService;

	/**
	 * Checks if a user has the given role directly assigned
	 * @param userUuid uuid of the user
	 * @param userRoleId id of the role to check
	 * @return true if the user has the role directly assigned
	 */
	public boolean hasRoleDirectly(String userUuid, Long userRoleId) {
		return currentAssignmentService.hasRoleDirectly(userUuid, userRoleId);
	}

	/**
	 * Returns the assignments for a given user
	 * @param user the user
	 * @return a set of CurrentAssignments
	 */
	public Set<CurrentAssignment> getByUser(User user) {
		return currentAssignmentService.findByUser(user);
	}

	public Set<CurrentAssignment> getByUserIncludingInactive(User user) {
		return currentAssignmentService.findByUserIncludingInactive(user);
	}

	public Set<CurrentAssignment> getByUserWithEagerRolesAndSystemsIncludingInactive(User user) {
		return currentAssignmentService.findByUserWithEagerRolesAndSystemsIncludingInactive(user);
	}

	/**
	 * Returns the roleGroup assignments for a given user
	 * @param user the user
	 * @return a set of CurrentAssignments
	 */
	public Set<CurrentAssignment> getAllRoleGroupAssignmentsByUserIncludingInactive(User user) {
		return currentAssignmentService.findByUserAndRoleGroupNotNullIncludingInactive(user);
	}

	/**
	 * Finds all assignments for a specific user in any of the given it systems
	 * @param user the user
	 * @param itSystems collection of itsystems to match
	 * @return a set of CurrentAssignments
	 */
	public Set<CurrentAssignment> getByUserAndItSystems(User user, List<ItSystem> itSystems) {
		return currentAssignmentService.findByUserInSystem(user, itSystems);
	}

	/**
	 * Som {@link #getByUserAndItSystems}, men eager-loader de relationer der kræves til
	 * NemLog-in serialisering (systemRoleAssignments → constraintValues → constraintType,
	 * postponedConstraints). Brug denne i stedet for manuelt at initialisere lazy collections.
	 */
	public Set<CurrentAssignment> getByUserAndItSystemsWithRoleDetails(User user, List<ItSystem> itSystems) {
		return currentAssignmentService.findByUserInSystemWithRoleDetails(user, itSystems);
	}

	/**
	 * Finds all users that have the specified role assigned, from any source
	 * @param userRole the userrole
	 * @return a set of Users
	 */
	public Set<User> getUsersWithUserRole(UserRole userRole) {
		return currentAssignmentService.findByUserRole(userRole).stream().map(CurrentAssignment::getUser).collect(Collectors.toSet());
	}

	/**
	 * Finds all users that have the specified role directly assigned
	 * @param userRole the userrole
	 * @return a set of Users
	 */
	public Set<User> getUsersWithUserRoleDirectlyAssigned(UserRole userRole) {
		// User-projektion frem for at hente CurrentAssignment-entiteter ind i persistence-konteksten.
		// Why: når kalderen efterfølgende sletter UserRole'en i samme transaktion, ville en managed
		// CurrentAssignment med .userRole-reference få Hibernate til at afbryde flush med
		// TransientPropertyValueException (jf. UserRoleCleanupService.deleteWithCleanup).
		return currentAssignmentService.findUsersByDirectlyAssignedUserRole(userRole);
	}

	/**
	 * Finds all users that have the specified roleGroup directly assigned
	 * @param roleGroup the userrole
	 * @return a set of Users
	 */
	public Set<User> getUsersWithRoleGroupDirectlyAssignedIncludingInactive(RoleGroup roleGroup) {
		return currentAssignmentService.findByDirectlyAssignedRoleGroupIncludingInactive(roleGroup).stream().map(CurrentAssignment::getUser).collect(Collectors.toSet());
	}

	/**
	 * Finds all users with a role in the given system
	 * @param system system to match
	 * @return a set of Users
	 */
	public Set<User> getUsersForSystem(ItSystem system){
		return currentAssignmentService.findByItSystem(system).stream()
			.map(CurrentAssignment::getUser)
			.collect(Collectors.toSet());
	}

	/**
	 * Returns true if the user has the specified role
	 * @param userUuid the users uuid
	 * @param userRoleId the userroles id
	 * @return true if the user is assigned the role, false otherwise
	 */
	public boolean hasUserRole(User user, UserRole userRole) {
		return currentAssignmentService.hasUserRole(user, userRole);
	}

	/**
	 * Finds all userroles for the specified systems, no matter how they were assigned
	 * @param user the user
	 * @param systems allowed systems
	 * @return a set of userroles for the user, filtered by the systems
	 */
	public Set<UserRole> getUserRolesByUserAndSystems(User user, Collection<ItSystem> systems) {
		return currentAssignmentService.findByUserInSystem(user, systems).stream().map(CurrentAssignment::getUserRole).collect(Collectors.toSet());
	}

	/**
	 * Finds all the assignments for the specified system, no matter how they were assigned
	 * @param system allowed system
	 * @return a set of CurrentAssignment filtered by the system
	 */
	public Set<CurrentAssignment> getBySystem(ItSystem system) {
		return currentAssignmentService.findByItSystem(system);
	}

	/**
	 * Finds all the directly assigned assignments for the specified system
	 * @param system allowed system
	 * @return a set of CurrentAssignment filtered by the system
	 */
	public Set<CurrentAssignment> getDirectlyAssignedInItSystem(ItSystem system) {
		return currentAssignmentService.findDirectlyAssignedByItSystem(system);
	}

	/**
	 * Finds all the directly assigned assignments for the specified user
	 * @param user the user
	 * @return a set of CurrentAssignment filtered by the user
	 */
	public Set<CurrentAssignment> getActiveDirectlyAssignedUserRolesForUser(User user) {
		return currentAssignmentService.findActiveDirectlyAssigned(user);
	}

	/**
	 * Finds all the directly assigned assignments for the specified user
	 * @param user the user
	 * @return a set of CurrentAssignment filtered by the user
	 */
	public Set<CurrentAssignment> getDirectlyAssignedUserRolesForUserIncludingInactive(User user) {
		return currentAssignmentService.findDirectAssignedIncludingInactive(user);
	}

	/**
	 * Finds all the directly assigned RoleGroup assignments for the specified user
	 * @param user the user
	 * @return a set of CurrentAssignment filtered by the user
	 */
	public Set<CurrentAssignment> getActiveDirectlyAssignedRoleGroupsForUser(User user) {
		return currentAssignmentService.findActiveDirectlyAssignedRoleGroups(user);
	}

	/**
	 * Finds all the assignments for the specified userRole, no matter how they were assigned
	 * @param userRole allowed userRole
	 * @return a set of CurrentAssignment filtered by the userRole
	 */
	public Set<CurrentAssignment> getByUserRole(UserRole userRole) {
		return currentAssignmentService.findByUserRole(userRole);
	}

	/**
	 * User-UUID-projektion frem for at hente CurrentAssignment-entiteter ind i persistence-konteksten.
	 * Why: når kalderen efterfølgende sletter UserRole'en i samme transaktion, ville en managed
	 * CurrentAssignment med .userRole-reference få Hibernate til at afbryde flush med
	 * TransientPropertyValueException (jf. UpdatedAssignmentCalculatorHook → ItSystemCleanupTask).
	 */
	public Set<String> getUserUuidsByUserRole(UserRole userRole) {
		return currentAssignmentService.findUserUuidsByUserRole(userRole);
	}

	/**
	 * Finds all the directly assigned assignments for the specified userRole
	 * @param userRole allowed userRole
	 * @return a set of CurrentAssignment filtered by the userRole
	 */
	public Set<CurrentAssignment> getByDirectlyAssignedUserRole(UserRole userRole) {
		return currentAssignmentService.findByDirectlyAssignedUserRole(userRole);
	}

	/**
	 * Finds all the assignments for the specified roleGroup, no matter how they were assigned (note: more than one CurrentAssignment pr roleGroup)
	 * @param roleGroup allowed roleGroup
	 * @return a set of CurrentAssignment filtered by the roleGroup
	 */
	public Set<CurrentAssignment> getByRoleGroup(RoleGroup roleGroup) {
		return currentAssignmentService.findByRoleGroup(roleGroup);
	}

	public Set<String> getUserUuidsByRoleGroup(RoleGroup roleGroup) {
		return currentAssignmentService.findUserUuidsByRoleGroup(roleGroup);
	}

	/**
	 * Count all users with the specified userRole directly assigned
	 * @param userRole allowed userRole
	 * @return the number of users with the role assigned
	 */
	public long countAllUsersWithDirectUserRole(UserRole userRole) {
		return currentAssignmentService.countUsersWithDirectlyAssignedUserRole(userRole);
	}

	/**
	 * Count all users with the specified roleGroup directly assigned
	 * @param roleGroup allowed roleGroup
	 * @return the number of users with the roleGroup assigned
	 */
	public long countAllUsersWithDirectRoleGroup(RoleGroup roleGroup) {
		return currentAssignmentService.countUsersWithDirectlyAssignedRoleGroup(roleGroup);
	}

	/**
	 * Finds all the assignments for the specified roleGroup and user, no matter how they were assigned (note: more than one CurrentAssignment pr roleGroup)
	 * @param roleGroup allowed roleGroup
	 * @param user the user
	 * @return a set of CurrentAssignment filtered by the roleGroup and the user
	 */
	public Set<CurrentAssignment> getByRoleGroupAndUserIncludingInactive(RoleGroup roleGroup, User user) {
		return currentAssignmentService.findByRoleGroupAndUserIncludingInactive(roleGroup, user);
	}

	/**
	 * Finds all the assignments for the specified userRole and user, no matter how they were assigned
	 * @param userRole allowed userRole
	 * @param user the user
	 * @return a set of CurrentAssignment filtered by the userRole and the user
	 */
	public Set<CurrentAssignment> getByUserRoleAndUserIncludingInactive(UserRole userRole, User user) {
		return currentAssignmentService.findByUserRoleAndUserIncludingInactive(userRole, user);
	}

	/**
	 * Finds all the assignments for the specified userRole, if they are assigned directly or through roleGroup
	 * @param userRole allowed userRole
	 * @return a set of CurrentAssignments
	 */
	public Set<CurrentAssignment> getByUserRoleDirectlyAssignedOrFromRoleGroupIncludingInactive(UserRole userRole) {
		return currentAssignmentService.findByUserRoleDirectlyAssignedOrFromRoleGroupIncludingInactive(userRole);
	}

	/**
	 * Finds all the active (startDate null or before today) assignments for the specified userRole, if they are assigned directly or through roleGroup
	 * @param userRole allowed userRole
	 * @return a set of active CurrentAssignments
	 */
	public Set<CurrentAssignment> getActiveByUserRoleDirectlyAssignedOrFromRoleGroup(UserRole userRole) {
		return currentAssignmentService.findActiveByUserRoleDirectlyAssignedOrFromRoleGroup(userRole);
	}
	
	/**
	 * Finds all active assignments for the given userRoles
	 * @param userRoles the collection of userRoles
	 * @return a set of active CurrentAssignments
	 */
	public Set<CurrentAssignment> getActiveByUserRoles(Set<UserRole> userRoles) {
		return currentAssignmentService.findActiveByUserRoles(userRoles);
	}
	
	public Set<CurrentAssignmentSmallProjection> getActiveByUserRolesAsProjection(Set<UserRole> userRoles) {
		return currentAssignmentService.findActiveByUserRolesAsProjection(userRoles);
	}

	/**
	 * Returns active assignments for a userRole (where startDate is null or not in the future)
	 * @param userRole the userRole
	 * @return a set of active CurrentAssignments
	 */
	public Set<CurrentAssignment> getActiveByUserRole(UserRole userRole) {
		return getActiveByUserRole(userRole, null);
	}
	
	/**
	 * Returns active assignments for a userRole (where startDate is null or not in the future), applying the consumer inside a readonly transaction
	 * @param userRole the userRole
	 * @param consumer the consumer
	 * @return a set of active CurrentAssignments
	 */
	@Transactional(readOnly = true)
	public Set<CurrentAssignment> getActiveByUserRole(UserRole userRole, Consumer<CurrentAssignment> consumer) {
		Set<CurrentAssignment> assignments = currentAssignmentService.findActiveByUserRole(userRole);
		
		if (consumer != null) {
			assignments.forEach(consumer);
		}
		
		return assignments;
	}

	/**
	 * Returns active assignments for a roleGroup (where startDate is null or not in the future)
	 * @param roleGroup the roleGroup
	 * @return a set of active CurrentAssignments
	 */
	public Set<CurrentAssignment> getActiveByRoleGroup(RoleGroup roleGroup) {
		return currentAssignmentService.findActiveByRoleGroup(roleGroup);
	}

	/**
	 * Returns unique roleGroup assignments for a user (one per roleGroup assignment, not per userRole)
	 * @param user the user
	 * @return a set of CurrentAssignments
	 */
	public Set<CurrentAssignment> getUniqueRoleGroupAssignmentsByUser(User user) {
		Set<CurrentAssignment> roleGroupAssignments = getAllRoleGroupAssignmentsByUserIncludingInactive(user);
		return getUniqueRoleGroupAssignments(roleGroupAssignments);
	}


	/**
	 * Returns active assignments for the specified it-system
	 * @param itSystem the itSystem
	 * @return a set of CurrentAssignments
	 */
	public Set<CurrentAssignment> getActiveAssignmentsByItSystem(ItSystem itSystem) {
		return currentAssignmentService.findActiveAssignmentsForItSystem(itSystem);
	}

	/**
	 * Returns active assignments for the specified it-systems
	 * @param itSystems the itSystems
	 * @return a set of CurrentAssignments
	 */
	public Set<CurrentAssignment> getActiveAssignmentsByItSystems(List<ItSystem> itSystems) {
		return currentAssignmentService.findActiveAssignmentsForItSystems(itSystems);
	}

	/**
	 * Returns unique userRole ids for all assigned userRoles (no matter assingment type)
	 * @return a set of userRole ids
	 */
	public Set<Long> findDistinctUserRoleIds() {
		return currentAssignmentService.findDistinctUserRoleIds();
	}

	/**
	 * Returns excepted assignments for a user
	 * @param user the user
	 * @return a set of CurrentExceptedAssignments
	 */
	public Set<CurrentExceptedAssignment> getExceptedByUser(User user) {
		return currentExceptedAssignmentService.getExceptedAssignmentsForUser(user);
	}

	/**
	 * Returns currentAssignments with startDate today and in itSystem
	 * @param itSystem the itSystem
	 * @return a set of userRole ids
	 */
	public Set<CurrentAssignment> findByStartDateTodayAndItSystem(ItSystem itSystem) {
		return currentAssignmentService.findByStartDateAndItSystem(LocalDate.now(), itSystem);
	}

	// helper methods bellow:

	/**
	 * Helper method to get an AssignedThrough enum from an assignment (userRole context)
	 * @param currentAssignment the assignment to check
	 * @return the corresponding AssignedThrough value for this assignment
	 */
	public AssignedThrough getAssignedThrough(CurrentAssignment currentAssignment) {
		AssignedThrough assignedThrough = AssignedThrough.DIRECT;

		boolean isFromRoleGroup = currentAssignment.getRoleGroup() != null;
		boolean isFromTitle = currentAssignment.getTitle() != null;
		boolean isFromOrgUnit = currentAssignment.getOrgUnit() != null;

		// userRoler nedarvet via en rollebuket vises som ROLEGROUP uanset om buketten
		// er tildelt direkte, via enhed eller via enhed+stilling — så Tildeling-kolonnen
		// peger på buketten i stedet for enheden (matcher 2026r1-adfærd)
		if (isFromRoleGroup) {
			assignedThrough = AssignedThrough.ROLEGROUP;
		} else if (isFromTitle) {
			assignedThrough = AssignedThrough.TITLE;
		} else if (isFromOrgUnit) {
			assignedThrough = AssignedThrough.ORGUNIT;
		}
		return assignedThrough;
	}

	/**
	 * Helper method to get an AssignedThrough enum from an assignment (roleGroup context)
	 * @param currentAssignment the assignment to check
	 * @return the corresponding AssignedThrough value for this assignment
	 */
	public AssignedThrough getAssignedThroughForRoleGroup(CurrentAssignment currentAssignment) {
		boolean isFromTitle = currentAssignment.getTitle() != null;
		boolean isFromOrgUnit = currentAssignment.getOrgUnit() != null;

		// the order of the checks bellow matters
		if (isFromTitle) {
			return AssignedThrough.TITLE;
		} else if (isFromOrgUnit) {
			return AssignedThrough.ORGUNIT;
		}

		return AssignedThrough.DIRECT;
	}

	/**
	 * Helper method to get AssignedThrough Name from an assignment
	 * @param assignment the assignment to check
	 * @param assignedThrough the assignedThrough type for the assignment
	 * @return the name AssignedThrough OU or role group for this assignment
	 */
	public String getAssignedThroughName(CurrentAssignment assignment, AssignedThrough assignedThrough) {
		String assignedThroughName = null;
		switch (assignedThrough) {
			case ROLEGROUP:
				if (assignment.getRoleGroup() != null) {
					assignedThroughName = assignment.getRoleGroup().getName();
				}
				break;
			case ORGUNIT:
			case TITLE:
				if (assignment.getOrgUnit() != null) {
					assignedThroughName = assignment.getOrgUnit().getName();
				}
				break;
			case DIRECT:
				// No assignedThroughName for direct assignments
				break;
			case POSITION:
				// not possible anymore
				break;
		}
		return assignedThroughName;
	}

	/**
	 * Helper method to get an AssignmentType enum from an assignment
	 * @param currentAssignment the assignment to check
	 * @return the corresponding AssignmentType value for this assignment
	 */
	public AssignmentType getAssignmentType(CurrentAssignment currentAssignment) {
		if (currentAssignment.getOrgUnit() == null) {
			if (currentAssignment.getRoleGroup() == null) {
				return AssignmentType.USER_USER_ROLE;
			} else {
				return AssignmentType.USER_ROLE_GROUP;
			}
		} else {
			if (currentAssignment.getRoleGroup() == null) {
				return AssignmentType.OU_USER_ROLE;
			} else {
				return AssignmentType.OU_ROLE_GROUP;
			}
		}
	}

	record RoleGroupKey(String userUuid, long assignmentId, AssignmentType assignmentType, long roleGroupId) {}
	/**
	 * Groups assignments by roleGroup assignment, returning one CurrentAssignment per unique
	 * combination of user, assignmentId, assignmentType, and roleGroupId.
	 * Only includes assignments that have a roleGroup.
	 * @param assignments the assignments to deduplicate
	 * @return a set with one assignment per unique roleGroup assignment
	 */
	public Set<CurrentAssignment> getUniqueRoleGroupAssignments(Set<CurrentAssignment> assignments) {

		Map<RoleGroupKey, CurrentAssignment> uniqueAssignments = new HashMap<>();

		for (CurrentAssignment assignment : assignments) {
			if (assignment.getRoleGroup() == null) {
				continue;
			}

			AssignmentType assignmentType = getAssignmentType(assignment);
			RoleGroupKey key = new RoleGroupKey(
				assignment.getUser().getUuid(),
				assignment.getAssignmentId(),
				assignmentType,
				assignment.getRoleGroup().getId()
			);

			uniqueAssignments.putIfAbsent(key, assignment);
		}

		return new HashSet<>(uniqueAssignments.values());
	}

	/**
	 * Groups excepted roleGroup assignments, returning one CurrentExceptedAssignment per unique
	 * combination of user, roleGroup, orgUnit, and title.
	 * Only includes exceptions that have a roleGroup.
	 * @param exceptedAssignments the excepted assignments to deduplicate
	 * @return a set with one excepted assignment per unique roleGroup exception
	 */
	public Set<CurrentExceptedAssignment> getUniqueExceptedRoleGroupAssignments(Set<CurrentExceptedAssignment> exceptedAssignments) {
		record ExceptedRoleGroupKey(String userUuid, Long roleGroupId, String ouUuid, String titleUuid, long assignmentId) {}

		Map<ExceptedRoleGroupKey, CurrentExceptedAssignment> uniqueAssignments = new HashMap<>();

		for (CurrentExceptedAssignment assignment : exceptedAssignments) {
			if (assignment.getExceptionRoleGroupId() == null) {
				continue;
			}

			ExceptedRoleGroupKey key = new ExceptedRoleGroupKey(
				assignment.getExceptionUserUuid(),
				assignment.getExceptionRoleGroupId(),
				assignment.getExceptionOuUuid(),
				assignment.getExceptionTitleUuid(),
				assignment.getExceptionAssignmentId()
			);

			uniqueAssignments.putIfAbsent(key, assignment);
		}

		return new HashSet<>(uniqueAssignments.values());
	}

	/**
	 * Gathers all role assignments for a user into a flat list of DTOs.
	 * Includes user roles, deduplicated role groups, excepted (negative) user roles, and excepted role groups.
	 */
	public List<RoleAssignedToUserDTO> getAssignmentsForUser(User user, Set<CurrentAssignment> currentAssignments) {

		// add all userRoles (no matter how they are assigned - also roleGroups)
		// skip roleGroup-only rows (empty role groups have no userRole); they are surfaced by the roleGroup pass below
		List<RoleAssignedToUserDTO> assignmentDTOs = new ArrayList<>(currentAssignments.stream()
			.filter(a -> a.getUserRole() != null)
			.map(a -> RoleAssignedToUserDTO.fromCurrentAssignmentUserRole(a, getAssignedThrough(a)))
			.toList());

		// add all roleGroups (deduplicated)
		Set<CurrentAssignment> roleGroupAssignments = getUniqueRoleGroupAssignments(currentAssignments);
		assignmentDTOs.addAll(roleGroupAssignments.stream()
			.map(a -> RoleAssignedToUserDTO.fromCurrentAssignmentRoleGroup(a, getAssignedThroughForRoleGroup(a)))
			.toList());

		// add negative/excepted assignments
		Set<CurrentExceptedAssignment> exceptedAssignments = getExceptedByUser(user);

		// Create a map of itSystemId to ItSystem for efficient lookup
		Map<Long, ItSystem> itSystemMap = itSystemService.getAll().stream()
			.collect(Collectors.toMap(ItSystem::getId, it -> it));

		// Add excepted userRoles
		assignmentDTOs.addAll(exceptedAssignments.stream()
			.filter(ea -> ea.getExceptionRoleGroupId() == null)
			.map(ea -> RoleAssignedToUserDTO.fromCurrentExceptedAssignmentUserRole(ea, itSystemMap.get(ea.getExceptionItSystemId())))
			.toList());

		// Add excepted roleGroups (deduplicated)
		Set<CurrentExceptedAssignment> uniqueExceptedRoleGroups = getUniqueExceptedRoleGroupAssignments(exceptedAssignments);
		assignmentDTOs.addAll(uniqueExceptedRoleGroups.stream()
			.map(RoleAssignedToUserDTO::fromCurrentExceptedAssignmentRoleGroup)
			.toList());

		return assignmentDTOs;
	}
}
