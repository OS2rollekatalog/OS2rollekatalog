package dk.digitalidentity.rc.service.assignment;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dk.digitalidentity.rc.dao.assignment.CurrentAssignmentDao;
import org.springframework.data.domain.PageRequest;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignment;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignmentConstraintValue;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.assignment.CurrentAssignment;
import dk.digitalidentity.rc.dao.model.assignment.CurrentAssignmentSmallProjection;

import org.hibernate.Hibernate;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CurrentAssignmentService {
	private final CurrentAssignmentDao currentAssignmentDao;
	private final HistoricAssignmentService historicAssignmentService;

	@Transactional
	public Set<User> saveAllForUsers(Map<User, Set<CurrentAssignment>> assignmentsByUser) {
		if (assignmentsByUser.isEmpty()) {
			return Set.of();
		}

		// 1 SELECT for alle brugere
		Set<CurrentAssignment> allExisting = currentAssignmentDao.findByUserIn(assignmentsByUser.keySet());
		Map<String, Map<String, CurrentAssignment>> existingByUserUuidAndHash = allExisting.stream()
			.collect(Collectors.groupingBy(
				ca -> ca.getUser().getUuid(),
				Collectors.toMap(CurrentAssignment::getRecordHash, Function.identity(), (e, ignored) -> e)
			));

		Set<CurrentAssignment> allToDelete = new HashSet<>();
		Set<CurrentAssignment> allToCreate = new HashSet<>();

		for (Map.Entry<User, Set<CurrentAssignment>> entry : assignmentsByUser.entrySet()) {
			Map<String, CurrentAssignment> existingByHash = existingByUserUuidAndHash.getOrDefault(entry.getKey().getUuid(), Map.of());
			Set<String> newHashes = entry.getValue().stream().map(CurrentAssignment::getRecordHash).collect(Collectors.toSet());

			existingByHash.entrySet().stream()
				.filter(e -> !newHashes.contains(e.getKey()))
				.map(Map.Entry::getValue)
				.forEach(allToDelete::add);

			entry.getValue().stream()
				.filter(a -> !existingByHash.containsKey(a.getRecordHash()))
				.forEach(allToCreate::add);
		}

		if (!allToDelete.isEmpty()) {
			historicAssignmentService.updateValidToFor(allToDelete, LocalDateTime.now());
			currentAssignmentDao.deleteAllById(allToDelete.stream().map(CurrentAssignment::getId).toList());
		}

		if (!allToCreate.isEmpty()) {
			historicAssignmentService.createFromCurrentAssignments(allToCreate);
			currentAssignmentDao.saveAll(allToCreate);
		}

		Set<User> changedUsers = new HashSet<>();
		allToDelete.stream().map(CurrentAssignment::getUser).forEach(changedUsers::add);
		allToCreate.stream().map(CurrentAssignment::getUser).forEach(changedUsers::add);
		return changedUsers;
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

	public Set<CurrentAssignment> findByUserIncludingInactive(User user) {
		return currentAssignmentDao.findByUser(user);
	}

	public Set<CurrentAssignment> findByUserWithEagerRolesAndSystemsIncludingInactive(User user) {
		return currentAssignmentDao.findByUserUuidWithEagerRolesAndSystems(user.getUuid());
	}

	public Set<CurrentAssignment> findByUserAndRoleGroupNotNullIncludingInactive(User user) {
		return currentAssignmentDao.findByUserUuidAndRoleGroupNotNull(user.getUuid());
	}

	public Set<CurrentAssignment> findByUserInSystem(User user, Collection<ItSystem> itSystems) {
		return currentAssignmentDao.findByUserAndItSystemIn(user, itSystems, LocalDate.now());
	}

	/**
	 * Som {@link #findByUserInSystem}, men med eager fetch af de relationer der kræves til
	 * NemLog-in serialisering. Brug denne frem for {@link #findByUserInSystem} efterfulgt af
	 * manuel initialisering af lazy collections.
	 */
	public Set<CurrentAssignment> findByUserInSystemWithRoleDetails(User user, Collection<ItSystem> itSystems) {
		List<CurrentAssignment> loaded = currentAssignmentDao.findByUserAndItSystemInWithRoleDetails(user, itSystems, LocalDate.now());

		// Tving initialisering af constraintType pr. constraintValue. EntityGraph'en
		// lister stien, men Hibernate 6 kan ikke pålideligt materialisere en ToOne
		// så dybt under flere collection-fetches — proxyen bliver liggende og kaster
		// LazyInitializationException når fullRoleSync tilgår den efter session-luk.
		for (CurrentAssignment ca : loaded) {
			UserRole userRole = ca.getUserRole();
			if (userRole == null || userRole.getSystemRoleAssignments() == null) {
				continue;
			}
			for (SystemRoleAssignment sra : userRole.getSystemRoleAssignments()) {
				if (sra.getConstraintValues() == null) {
					continue;
				}
				for (SystemRoleAssignmentConstraintValue cv : sra.getConstraintValues()) {
					Hibernate.initialize(cv.getConstraintType());
				}
			}
		}

		return new LinkedHashSet<>(loaded);
	}

	public Set<CurrentAssignment> findByUserRole(UserRole userRole) {
		return  currentAssignmentDao.findByUserRole(userRole, LocalDate.now());
	}

	public Set<String> findUserUuidsByUserRole(UserRole userRole) {
		return currentAssignmentDao.findUserUuidsByUserRole(userRole, LocalDate.now());
	}

	public Set<CurrentAssignment> findByDirectlyAssignedUserRole(UserRole userRole) {
		return  currentAssignmentDao.findActiveAssignedDirectUserRoleOnly(userRole, LocalDate.now());
	}

	public Set<User> findUsersByDirectlyAssignedUserRole(UserRole userRole) {
		return currentAssignmentDao.findActiveUsersByDirectlyAssignedUserRoleOnly(userRole, LocalDate.now());
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

	public Set<CurrentAssignment> findActiveByUserRoles(Set<UserRole> userRoles) {
		return currentAssignmentDao.findActiveAssigned(userRoles, LocalDate.now());
	}
	
	public Set<CurrentAssignmentSmallProjection> findActiveByUserRolesAsProjection(Set<UserRole> userRoles) {
		return currentAssignmentDao.findActiveAssignedAsProjection(userRoles, LocalDate.now());
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

	public List<String> findUuidsOfDeletedUsersWithCurrentAssignments(int limit) {
		return currentAssignmentDao.findUuidsOfDeletedUsersWithCurrentAssignments(PageRequest.of(0, limit));
	}

	public Set<Long> findDistinctUserRoleIds() {
		return currentAssignmentDao.findDistinctUserRoleIds();
	}

	public Set<CurrentAssignment> findByStartDateAndItSystem(LocalDate startDate, ItSystem itSystem) {
		return currentAssignmentDao.findByStartDateAndItSystem(startDate, itSystem);
	}
}
