package dk.digitalidentity.rc.service.assignment;

import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.OrgUnitAssignment;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.Title;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.assignment.CurrentAssignment;
import dk.digitalidentity.rc.dao.model.assignment.CurrentExceptedAssignment;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.assignment.rules.AssignmentRule;
import dk.digitalidentity.rc.service.assignment.rules.AssignmentRuleEvaluator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static dk.digitalidentity.rc.service.assignment.mapper.CurrentAssignmentMapper.toCurrentAssignment;
import static dk.digitalidentity.rc.service.assignment.mapper.CurrentExceptedAssignmentMapper.toAssignmentException;

@Slf4j
@Service
@RequiredArgsConstructor
public class CurrentAssignmentCalculator {
	private final AssignmentRuleEvaluator ruleEvaluator;
	private final OrgUnitService orgUnitService;

	@Transactional(propagation = Propagation.MANDATORY)
	public ImmutablePair<Set<CurrentAssignment>, Set<CurrentExceptedAssignment>> calculateAllAssignmentsForUser(User user) {
		// Slettede brugere har ingen aktuelle tildelinger. Invariant: current_assignment indeholder ikke
		// rækker for deleted=true. saveAllForUsers ser tom output → diff'er eksisterende rækker væk.
		// Genaktivering går via interceptActivateUser → queueForRecalculation, så rækkerne kommer tilbage.
		if (user.isDeleted()) {
			return new ImmutablePair<>(Collections.emptySet(), Collections.emptySet());
		}

		LocalDate today = LocalDate.now();

		final Set<CurrentAssignment> allUserAssignments = calculateDirectUserRoleAssignments(user, today);
		final Set<CurrentExceptedAssignment> assignmentExceptions = new HashSet<>();

		// Directly assigned rolegroups
		allUserAssignments.addAll(
			calculateDirectRoleGroupAssignments(user, today)
		);

		// Process positions
		List<Position> positions = user.getPositions();

		for (Position position : positions) {
			boolean inheritsFromOu = !position.isDoNotInherit();
			if (inheritsFromOu) {
				// preload to reduce SQL query count
				orgUnitService.findWithAllAncestors(position.getOrgUnit().getUuid()).stream().forEach(o -> {
					o.getUserRoleAssignments().size();
					o.getRoleGroupAssignments().size();
				});

				Title title = position.getTitle();

				allUserAssignments.addAll(
					calculateOrgUnitAssignments(position, position.getOrgUnit(), title, new HashSet<>(), assignmentExceptions)
				);
			}
		}

		// Process manager/substitute assignments
		Set<OrgUnit> managerSubstituteOUs = new HashSet<>();

		// Collect OUs where user is manager
		if (user.getManagedOrgUnits() != null && !user.getManagedOrgUnits().isEmpty()) {
			managerSubstituteOUs.addAll(user.getManagedOrgUnits());
		}

		// Collect OUs where user is substitute
		if (user.getSubstituteFor() != null && !user.getSubstituteFor().isEmpty()) {
			for (var substitute : user.getSubstituteFor()) {
				managerSubstituteOUs.add(substitute.getOrgUnit());
			}
		}

		if (!managerSubstituteOUs.isEmpty()) {
			for (OrgUnit managerOfOU : managerSubstituteOUs) {
				allUserAssignments.addAll(
					calculateOrgUnitManagerSubstituteAssignments(user, managerOfOU, managerOfOU, new HashSet<>(), assignmentExceptions)
				);
			}
		}

		return new ImmutablePair<>(allUserAssignments, assignmentExceptions);
	}

	private Set<CurrentAssignment> calculateDirectUserRoleAssignments(User user, LocalDate today) {
		return user.getUserRoleAssignments().stream()
			.filter(ura -> filterByNotEnded(ura.getStopDate(), today))
			.map(ura -> toCurrentAssignment(ura, user, resolveResponsibleOuForManager(user, ura.getOrgUnit())))
			.collect(Collectors.toSet());
	}

	private Set<CurrentAssignment> calculateDirectRoleGroupAssignments(User user, LocalDate today) {
		return user.getRoleGroupAssignments().stream()
			.filter(ura -> filterByNotEnded(ura.getStopDate(), today))
			.flatMap(rga -> toCurrentAssignment(rga, user, resolveResponsibleOuForManager(user, rga.getOrgUnit())).stream())
			.collect(Collectors.toSet());
	}

	private boolean filterByNotEnded(LocalDate endDate, LocalDate today) {
		// We need to include assignments that are active in the future, as we need to display them in UI several places.
		// Therefore we do not check the startDate, we only check if the assignment has ended
		return endDate == null // no end date means active indefinitely
			|| endDate.isAfter(today); // if enddate is today or before, it has already ended
	}

	private Set<CurrentAssignment> calculateOrgUnitAssignments(Position position, OrgUnit orgUnit, Title title, Set<String> processedOUUuids, Set<CurrentExceptedAssignment> assignmentExceptions) {
		if (orgUnit == null) {
			// if there is no orgunit, we have reached the top of the hierachy
			return Set.of();
		}

		if (!processedOUUuids.add(orgUnit.getUuid())) {
			return Set.of();
		}

		// userroles
		Set<CurrentAssignment> userRoleAssignments = calculateOrgUnitUserRoleAssignments(position, orgUnit, title, assignmentExceptions);

		//rolegroups
		Set<CurrentAssignment> roleGroupAssignments = calculateOrgunitRoleGroupAssignments(position, orgUnit, title, assignmentExceptions);

		// get roles from parent OU
		Set<CurrentAssignment> parentAssignments = calculateOrgUnitAssignments(position, orgUnit.getParent(), title, processedOUUuids, assignmentExceptions);

		Set<CurrentAssignment> result = new HashSet<>(userRoleAssignments.size() + roleGroupAssignments.size() + parentAssignments.size());
		result.addAll(userRoleAssignments);
		result.addAll(roleGroupAssignments);
		result.addAll(parentAssignments);

		return result;
	}


	private Set<CurrentAssignment> calculateOrgUnitUserRoleAssignments(final Position position, final OrgUnit orgUnit,
																	   final Title title, Set<CurrentExceptedAssignment> assignmentExceptions) {

		final var groupedAssignments = orgUnit.getUserRoleAssignments().stream()
			.collect(Collectors.groupingBy(a -> ruleEvaluator.applies(a, position.getUser(), position, orgUnit)));
		final var assignedOrgUnitRoles = groupedAssignments.getOrDefault(AssignmentRule.AssignmentAppliesResult.POSITIVE, Collections.emptyList());
		final var negatedOrgUnitRoles = groupedAssignments.getOrDefault(AssignmentRule.AssignmentAppliesResult.NEGATIVE, Collections.emptyList());
		negatedOrgUnitRoles.stream()
			.map(a -> toAssignmentException(position.getUser(), title, a, position.getOrgUnit()))
			.forEach(assignmentExceptions::add);
		// If the user is the manager of the responsible OU, redirect to parent with a different manager
		final OrgUnit positionResponsibleOu = resolveResponsibleOuForManager(position.getUser(), position.getOrgUnit());
		return assignedOrgUnitRoles.stream()
			.map(a -> toCurrentAssignment(a, position.getUser(), title, responsibleOuFor(a, position.getUser(), orgUnit, positionResponsibleOu)))
			.collect(Collectors.toSet());
	}

	private Set<CurrentAssignment> calculateOrgunitRoleGroupAssignments(final Position position, OrgUnit orgUnit, Title title, Set<CurrentExceptedAssignment> assignmentExceptions) {

		final var groupedRoleGroupAssignments = orgUnit.getRoleGroupAssignments().stream()
			.collect(Collectors.groupingBy(a -> ruleEvaluator.applies(a, position.getUser(), position, orgUnit)));
		final var assignedOrgUnitGroups = groupedRoleGroupAssignments.getOrDefault(AssignmentRule.AssignmentAppliesResult.POSITIVE, Collections.emptyList());
		final var negatedOrgUnitGroups = groupedRoleGroupAssignments.getOrDefault(AssignmentRule.AssignmentAppliesResult.NEGATIVE, Collections.emptyList());
		negatedOrgUnitGroups.stream()
			.flatMap(a -> toAssignmentException(position.getUser(), title, a, position.getOrgUnit()).stream())
			.forEach(assignmentExceptions::add);
		// If the user is the manager of the responsible OU, redirect to parent with a different manager
		final OrgUnit positionResponsibleOu = resolveResponsibleOuForManager(position.getUser(), position.getOrgUnit());
		return assignedOrgUnitGroups.stream()
			.flatMap(a -> toCurrentAssignment(a, position.getUser(), responsibleOuFor(a, position.getUser(), orgUnit, positionResponsibleOu)).stream())
			.collect(Collectors.toSet());
	}

	/**
	 * Funktions-baserede tildelinger gives på (User, OrgUnit) — uafhængigt af stillinger.
	 * Når flere stillinger walker forbi samme arvede assignment, må de ikke producere
	 * forskellige responsibleOu'er (det fører til duplikerede current_assignment-rækker
	 * for samme rolle på samme bruger). Lås responsibleOu til selve assignment-OU'en
	 * for funktions-baserede tildelinger, så de kollapser deterministisk på recordHash.
	 */
	private OrgUnit responsibleOuFor(final OrgUnitAssignment assignment, final User user, final OrgUnit assignmentOrgUnit, final OrgUnit positionResponsibleOu) {
		if (assignment.isContainsFunctions()) {
			return resolveResponsibleOuForManager(user, assignmentOrgUnit);
		}
		return positionResponsibleOu;
	}

	// Manager/substitute based assignments
	private Set<CurrentAssignment> calculateOrgUnitManagerSubstituteAssignments(User user, OrgUnit managerOfOU, OrgUnit currentOU, Set<String> processedOUUuids, Set<CurrentExceptedAssignment> assignmentExceptions) {
		if (currentOU == null) {
			return Set.of();
		}

		if (!processedOUUuids.add(currentOU.getUuid())) {
			return Set.of();
		}

		// User roles for this OU based on manager/substitute relation
		Set<CurrentAssignment> userRoleAssignments = calculateOrgUnitUserRoleAssignmentsForManagerSubstitute(user, managerOfOU, currentOU, assignmentExceptions);

		// Role groups for this OU based on manager/substitute relation
		Set<CurrentAssignment> roleGroupAssignments = calculateOrgUnitRoleGroupAssignmentsForManagerSubstitute(user, managerOfOU, currentOU, assignmentExceptions);

		// Get roles from parent OU (if inherit is enabled on assignments)
		Set<CurrentAssignment> parentAssignments = calculateOrgUnitManagerSubstituteAssignments(user, managerOfOU, currentOU.getParent(), processedOUUuids, assignmentExceptions);

		Set<CurrentAssignment> result = new HashSet<>(userRoleAssignments.size() + roleGroupAssignments.size() + parentAssignments.size());
		result.addAll(userRoleAssignments);
		result.addAll(roleGroupAssignments);
		result.addAll(parentAssignments);

		return result;
	}

	private Set<CurrentAssignment> calculateOrgUnitUserRoleAssignmentsForManagerSubstitute(final User user, final OrgUnit managerOfOU, final OrgUnit currentOU,
		Set<CurrentExceptedAssignment> assignmentExceptions) {
		final var groupedAssignments = currentOU.getUserRoleAssignments().stream()
			.collect(Collectors.groupingBy(a -> ruleEvaluator.applies(a, user, managerOfOU, currentOU)));
		final var assignedOrgUnitRoles = groupedAssignments.getOrDefault(AssignmentRule.AssignmentAppliesResult.POSITIVE, Collections.emptyList());
		final var negatedOrgUnitRoles = groupedAssignments.getOrDefault(AssignmentRule.AssignmentAppliesResult.NEGATIVE, Collections.emptyList());

		negatedOrgUnitRoles.stream()
			.map(a -> toAssignmentException(user, null, a, managerOfOU))
			.forEach(assignmentExceptions::add);

		// Managers cannot attest themselves
		final OrgUnit responsibleOu = resolveResponsibleOuForManager(user, managerOfOU);
		return assignedOrgUnitRoles.stream()
			.map(a -> toCurrentAssignment(a, user, null, responsibleOu))
			.collect(Collectors.toSet());
	}

	private Set<CurrentAssignment> calculateOrgUnitRoleGroupAssignmentsForManagerSubstitute(final User user, final OrgUnit managerOfOU, final OrgUnit currentOU,
		Set<CurrentExceptedAssignment> assignmentExceptions) {
		final var groupedRoleGroupAssignments = currentOU.getRoleGroupAssignments().stream()
			.collect(Collectors.groupingBy(a -> ruleEvaluator.applies(a, user, managerOfOU, currentOU)));
		final var assignedOrgUnitGroups = groupedRoleGroupAssignments.getOrDefault(AssignmentRule.AssignmentAppliesResult.POSITIVE, Collections.emptyList());
		final var negatedOrgUnitGroups = groupedRoleGroupAssignments.getOrDefault(AssignmentRule.AssignmentAppliesResult.NEGATIVE, Collections.emptyList());

		negatedOrgUnitGroups.stream()
			.flatMap(a -> toAssignmentException(user, null, a, managerOfOU).stream())
			.forEach(assignmentExceptions::add);

		// Managers cannot attest themselves.
		final OrgUnit responsibleOu = resolveResponsibleOuForManager(user, managerOfOU);
		return assignedOrgUnitGroups.stream()
			.flatMap(a -> toCurrentAssignment(a, user, responsibleOu).stream())
			.collect(Collectors.toSet());
	}

	/**
	 * Auser must not attest their own access when they are the manager of the responsible OU.
	 * Walks up the OU hierarchy to find the nearest ancestor whose manager is not {@code user}.
	 * Substitutes are not the OU manager, so this returns {@code ou} unchanged for them —
	 * their assignments remain under the actual OU manager's attestation queue.
	 */
	private OrgUnit resolveResponsibleOuForManager(final User user, final OrgUnit ou) {
		if (ou == null) {
			return null;
		}
		return orgUnitService.findParentOrgUnitWithDifferentManager(ou, user.getUuid())
				.orElse(ou);
	}
}
