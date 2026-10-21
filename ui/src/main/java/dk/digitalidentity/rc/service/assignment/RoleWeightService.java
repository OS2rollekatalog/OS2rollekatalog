package dk.digitalidentity.rc.service.assignment;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dk.digitalidentity.rc.dao.UserRoleDao;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.assignment.CurrentAssignment;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RoleWeightService {
	private final UserRoleDao userRoleDao;
	private final CurrentAssignmentService currentAssignmentService;

	/**
	 * Uuids of the users that hold the role in vain, because they also hold a heavier role in the same it system
	 */
	@Transactional(readOnly = true)
	public Set<String> findOutweighedHolderUuids(UserRole userRole) {
		Set<Long> heavierRoleIds = weightsFor(Set.of(userRole.getItSystem().getId())).rolesOutweighing(userRole.getId());
		if (heavierRoleIds.isEmpty()) {
			return Set.of();
		}
		return currentAssignmentService.findActiveUserUuidsByUserRoleIds(heavierRoleIds);
	}

	/**
	 * Ids of the given assignments of a single user that grant nothing, because the user also has an
	 * active assignment of a heavier role in the same it system
	 */
	@Transactional(readOnly = true)
	public Set<Long> findOutweighedAssignmentIds(Collection<CurrentAssignment> userAssignments) {
		List<CurrentAssignment> roleAssignments = userAssignments.stream()
			.filter(ca -> !ca.isRoleGroupOnly())
			.toList();
		if (roleAssignments.isEmpty()) {
			return Set.of();
		}

		RoleWeights weights = weightsFor(roleAssignments.stream()
			.map(ca -> ca.getItSystem().getId())
			.collect(Collectors.toSet()));

		LocalDate today = LocalDate.now();
		Map<Long, Set<Long>> activeRoleIdsByItSystemId = roleAssignments.stream()
			.filter(ca -> isActiveOn(ca, today))
			.collect(Collectors.groupingBy(ca -> ca.getItSystem().getId(),
				Collectors.mapping(ca -> ca.getUserRole().getId(), Collectors.toSet())));

		return roleAssignments.stream()
			.filter(ca -> activeRoleIdsByItSystemId.getOrDefault(ca.getItSystem().getId(), Set.of()).stream()
				.anyMatch(candidate -> weights.outweighs(candidate, ca.getUserRole().getId())))
			.map(CurrentAssignment::getId)
			.collect(Collectors.toSet());
	}

	// same as the date filter in CurrentAssignmentDao: an assignment still counts on its stop date
	private static boolean isActiveOn(CurrentAssignment assignment, LocalDate day) {
		return (assignment.getStartDate() == null || !assignment.getStartDate().isAfter(day))
			&& (assignment.getStopDate() == null || !assignment.getStopDate().isBefore(day));
	}

	private RoleWeights weightsFor(Set<Long> itSystemIds) {
		return new RoleWeights(userRoleDao.findHeaviestSystemRoleWeights(itSystemIds));
	}
}
