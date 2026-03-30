package dk.digitalidentity.rc.service.assignment;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dk.digitalidentity.rc.dao.assignment.CurrentAssignmentDao;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.assignment.CurrentAssignment;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CurrentAssignmentService {
	private final CurrentAssignmentDao currentAssignmentDao;
	private final HistoricAssignmentService historicAssignmentService;

	/**
	 * Saves all assignments for a single user, by deleting any assignment in DB not matching the new assignments, and then Upserting
	 * @param user the user with the assignments
	 * @param assignments the new assignments for the user. Replaces any already in db.
	 */
	@Transactional
	public void saveAll(User user, Set<CurrentAssignment> assignments) {
		// Extract all record hashes
		Set<String> newRecordHashes = assignments.stream()
			.map(CurrentAssignment::getRecordHash)
			.collect(Collectors.toSet());

		// find all existing for this user
		Set<CurrentAssignment> existingAssignments = currentAssignmentDao.findByUser(user);
		Set<String> existingRecordHashes = existingAssignments.stream().map(CurrentAssignment::getRecordHash).collect(Collectors.toSet());

		// maps for quicker lookup
		Map<String, CurrentAssignment> existingByHash = existingAssignments.stream()
			.collect(Collectors.toMap(CurrentAssignment::getRecordHash, Function.identity(), (existing, _) -> existing));

		Map<String, CurrentAssignment> newByHash = assignments.stream()
			.collect(Collectors.toMap(CurrentAssignment::getRecordHash, Function.identity(), (existing, _) -> existing));

		// existing that does not match a recordhash in new set gets "deleted"
		Set<String> toDeleteRecordHashes = new HashSet<>(existingRecordHashes);
		toDeleteRecordHashes.removeAll(newRecordHashes);
		Set<CurrentAssignment> toDelete = toDeleteRecordHashes.stream()
			.map(existingByHash::get)
			.collect(Collectors.toSet());

		if (toDelete.size() > 0) {
			// Deleted assignments corresponding historic assignments (by recordhash) get their validTo updated
			historicAssignmentService.updateValidToFor(toDelete, LocalDateTime.now());

			// delete from current table
			currentAssignmentDao.deleteAllById(toDelete.stream().map(CurrentAssignment::getId).toList());
		}

		// any assignment from new hash not already existing gets created
		Set<String> toCreateRecordHashes = new HashSet<>(newRecordHashes);
		toCreateRecordHashes.removeAll(existingRecordHashes);
		Set<CurrentAssignment> toCreate = toCreateRecordHashes.stream()
			.map(newByHash::get)
			.collect(Collectors.toSet());

		Set<CurrentAssignment> toSave = new HashSet<>();
		toSave.addAll(toCreate);

		// newly created assignments also get a corresponding historic assignment, with the same recordhash
		if (toCreate.size() > 0) {
			historicAssignmentService.createFromCurrentAssignments(toCreate);
		}

		if (toSave.size() > 0) {
			currentAssignmentDao.saveAll(toSave);
		}
	}

	public boolean hasRoleDirectly(String userUuid, Long userRoleId) {
		return currentAssignmentDao.existsByUser_UuidAndUserRole_IdAndRoleGroupNullAndOrgUnitNullAndTitleNull(userUuid, userRoleId);
	}

	public boolean hasUserRole(User user, UserRole userRole) {
		return currentAssignmentDao.countByUserRole(user, userRole, LocalDate.now()) > 0;
	}

	public Set<CurrentAssignment> findByUser(User user) {
		return currentAssignmentDao.findByUser(user, LocalDate.now());
	}

	// TODO: not used
	public Set<CurrentAssignment> findByUserIncludingInactive(User user) {
		return currentAssignmentDao.findByUser(user);
	}

	public Set<CurrentAssignment> findByUserWithEagerRolesAndSystemsIncludingInactive(User user) {
		return currentAssignmentDao.findByUserUuidWithEagerRolesAndSystems(user.getUuid());
	}

	public Set<CurrentAssignment> findByUserAndRoleGroupNotNullIncludingInactive(User user) {
		return currentAssignmentDao.findByUserUuidAndRoleGroupNotNull(user.getUuid());
	}

	public Set<CurrentAssignment> findByUserAndRoleGroupNullIncludingInactive(User user) {
		return currentAssignmentDao.findByUserUuidAndRoleGroupNull(user.getUuid());
	}

	public Set<CurrentAssignment> findByUserInSystem(User user, Collection<ItSystem> itSystems) {
		return currentAssignmentDao.findByUserAndItSystemIn(user, itSystems, LocalDate.now());
	}

	public Set<CurrentAssignment> findByUserRole(UserRole userRole) {
		return  currentAssignmentDao.findByUserRole(userRole, LocalDate.now());
	}

	public Set<CurrentAssignment> findByDirectlyAssignedUserRole(UserRole userRole) {
		return  currentAssignmentDao.findActiveAssignedDirectUserRoleOnly(userRole, LocalDate.now());
	}

	public Set<CurrentAssignment> findActiveByUserRoleDirectlyAssignedOrFromRoleGroup(UserRole userRole) {
		return currentAssignmentDao.findActiveAssignedDirect(userRole, LocalDate.now());
	}

	// Note: This returns one CurrentAssignment per userRole contained in the roleGroup.
	// To get one assignment per unique roleGroup assignment, use AssignmentService.getUniqueRoleGroupAssignments()
	public Set<CurrentAssignment> findByDirectlyAssignedRoleGroupIncludingInactive(RoleGroup roleGroup) {
		return currentAssignmentDao.findByRoleGroupAndOrgUnitNullAndTitleNull(roleGroup);
	}

	public Set<CurrentAssignment> findByItSystem(ItSystem itSystem) {
		return currentAssignmentDao.findByItSystem(itSystem, LocalDate.now());
	}

	public Set<CurrentAssignment> findDirectlyAssignedByItSystem(ItSystem itSystem) {
		return currentAssignmentDao.findActiveDirectAssignmentsFromItSystem(itSystem, LocalDate.now());
	}

	public Set<CurrentAssignment> findByRoleGroup(RoleGroup roleGroup) {
		return currentAssignmentDao.findByRoleGroup(roleGroup, LocalDate.now());
	}

	public Set<String> findUserUuidsByRoleGroup(RoleGroup roleGroup) {
		return currentAssignmentDao.findUserUuidsByRoleGroup(roleGroup, LocalDate.now());
	}

	public Set<CurrentAssignment> findByRoleGroupAndUserIncludingInactive(RoleGroup roleGroup, User user) {
		return currentAssignmentDao.findByRoleGroupAndUser(roleGroup, user);
	}

	public Set<CurrentAssignment> findByUserRoleAndUserIncludingInactive(UserRole userRole, User user) {
		return currentAssignmentDao.findByUserRoleAndUser(userRole, user);
	}

	public long countUsersWithDirectlyAssignedUserRole(UserRole userRole) {
		return currentAssignmentDao.countDistinctUserByUserRoleAndRoleGroupNullAndOrgUnitNullAndTitleNull(userRole);
	}

	public long countUsersWithDirectlyAssignedRoleGroup(RoleGroup roleGroup) {
		return currentAssignmentDao.countDistinctUserByRoleGroupAndOrgUnitNullAndTitleNull(roleGroup);
	}

	public Set<CurrentAssignment> findActiveByUserRole(UserRole userRole) {
		return currentAssignmentDao.findActiveAssigned(userRole, LocalDate.now());
	}

	public Set<CurrentAssignment> findActiveByRoleGroup(RoleGroup roleGroup) {
		return currentAssignmentDao.findActiveAssignedThroughRoleGroup(roleGroup, LocalDate.now());
	}

	public Set<CurrentAssignment> findActiveAssignmentsForItSystem(ItSystem itSystem) {
		return currentAssignmentDao.findActiveAssignmentsForItSystem(itSystem, LocalDate.now());
	}

	public Set<CurrentAssignment> findActiveAssignmentsForItSystems(List<ItSystem> itSystems) {
		return currentAssignmentDao.findActiveAssignmentsForItSystems(itSystems, LocalDate.now());
	}

	public Set<CurrentAssignment> findByUserRoleDirectlyAssignedOrFromRoleGroupIncludingInactive(UserRole userRole) {
		return currentAssignmentDao.findByUserRoleAndOrgUnitNullAndTitleNull(userRole);
	}

	public Set<CurrentAssignment> findActiveDirectlyAssigned(User user) {
		return currentAssignmentDao.findActiveDirectAssignedThroughUserRoles(user, LocalDate.now());
	}

	public Set<CurrentAssignment> findDirectAssignedIncludingInactive(User user) {
		return currentAssignmentDao.findDirectAssignedThroughUserRolesIncludingInactive(user);
	}

	public Set<CurrentAssignment> findActiveDirectlyAssignedRoleGroups(User user) {
		return currentAssignmentDao.findActiveDirectAssignedThroughRoleGroups(user, LocalDate.now());
	}

	public Set<Long> findDistinctUserRoleIds() {
		return currentAssignmentDao.findDistinctUserRoleIds();
	}

	public Set<CurrentAssignment> findByStartDateAndItSystem(LocalDate startDate, ItSystem itSystem) {
		return currentAssignmentDao.findByStartDateAndItSystem(startDate, itSystem);
	}
}
