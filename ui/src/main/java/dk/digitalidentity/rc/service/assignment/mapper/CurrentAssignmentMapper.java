package dk.digitalidentity.rc.service.assignment.mapper;

import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.OrgUnitRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.OrgUnitUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.PostponedConstraint;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.RoleGroupUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.Title;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.UserRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.UserUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.assignment.CurrentAssignment;
import dk.digitalidentity.rc.dao.model.assignment.CurrentAssignmentPostponedConstraint;
import dk.digitalidentity.rc.dao.model.enums.ContainsTitles;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class CurrentAssignmentMapper {

	public static CurrentAssignment toCurrentAssignment(UserUserRoleAssignment userRoleAssignment, User user, OrgUnit attestationResponsibleOrgUnit) {
		CurrentAssignment currentAssignment = new CurrentAssignment();
		currentAssignment.setCreatedAt(LocalDateTime.now());
		currentAssignment.setUpdatedAt(LocalDateTime.now());
		currentAssignment.setStartDate(userRoleAssignment.getStartDate());
		currentAssignment.setStopDate(userRoleAssignment.getStopDate());
		currentAssignment.setUser(user);
		currentAssignment.setUserRole(userRoleAssignment.getUserRole());
		currentAssignment.setItSystem(userRoleAssignment.getUserRole().getItSystem());
		currentAssignment.setAssignmentId(userRoleAssignment.getId());
		currentAssignment.setResponsibleOrgUnit(attestationResponsibleOrgUnit);
		currentAssignment.setCaseNumber(userRoleAssignment.getCaseNumber());
		currentAssignment.setAssignedBy(userRoleAssignment.getAssignedByName() + " (" + userRoleAssignment.getAssignedByUserId() + ")");

		currentAssignment.setPostponedConstraints(
			Optional.ofNullable(userRoleAssignment.getPostponedConstraints())
				.orElse(List.of())
				.stream()
				.map(pc -> toCurrentAssignmentPostponedConstraint(pc, currentAssignment))
				.filter(Objects::nonNull)
				.collect(Collectors.toSet())
		);

		currentAssignment.setConstraintSignature(buildConstraintSignature(userRoleAssignment.getUserRole()));
		currentAssignment.setRecordHash(currentAssignment.generateRecordHash());

		return currentAssignment;
	}

	public static Set<CurrentAssignment> toCurrentAssignment(UserRoleGroupAssignment assignment, User user, OrgUnit attestationResponsibleOrgUnit) {
		Set<CurrentAssignment> currentAssignments = new HashSet<>();
		for (RoleGroupUserRoleAssignment userRoleAssignment : assignment.getRoleGroup().getUserRoleAssignments()) {
			CurrentAssignment currentAssignment = new CurrentAssignment();
			currentAssignment.setCreatedAt(LocalDateTime.now());
			currentAssignment.setUpdatedAt(LocalDateTime.now());
			currentAssignment.setStartDate(assignment.getStartDate());
			currentAssignment.setStopDate(assignment.getStopDate());
			currentAssignment.setUser(user);
			currentAssignment.setUserRole(userRoleAssignment.getUserRole());
			currentAssignment.setItSystem(userRoleAssignment.getUserRole().getItSystem());
			currentAssignment.setRoleGroup(assignment.getRoleGroup());
			currentAssignment.setAssignmentId(assignment.getId());
			currentAssignment.setResponsibleOrgUnit(attestationResponsibleOrgUnit);
			currentAssignment.setAssignedBy(assignment.getAssignedByName() + " (" + assignment.getAssignedByUserId() + ")");
			currentAssignment.setCaseNumber(assignment.getCaseNumber());

			currentAssignment.setPostponedConstraints(Set.of());
			currentAssignment.setConstraintSignature(buildConstraintSignature(userRoleAssignment.getUserRole()));
			currentAssignment.setRecordHash(currentAssignment.generateRecordHash());
			currentAssignments.add(currentAssignment);
		}

		if (currentAssignments.isEmpty()) {
			// a role group with no userroles must still be represented so the assignment stays visible
			currentAssignments.add(roleGroupOnlyAssignment(assignment.getRoleGroup(), user, assignment.getId(),
				assignment.getStartDate(), assignment.getStopDate(),
				assignment.getAssignedByName() + " (" + assignment.getAssignedByUserId() + ")",
				assignment.getCaseNumber(), null, attestationResponsibleOrgUnit));
		}

		return currentAssignments;
	}

	public static CurrentAssignment toCurrentAssignment(OrgUnitUserRoleAssignment assignment, User user, Title title, OrgUnit attestationResponsibleOrgUnit) {

		OrgUnit orgunit = assignment.getOrgUnit();
		UserRole userRole = assignment.getUserRole();

		CurrentAssignment currentAssignment = new CurrentAssignment();
		currentAssignment.setCreatedAt(LocalDateTime.now());
		currentAssignment.setUpdatedAt(LocalDateTime.now());
		currentAssignment.setStartDate(assignment.getStartDate());
		currentAssignment.setStopDate(assignment.getStopDate());
		currentAssignment.setUser(user);
		currentAssignment.setUserRole(userRole);
		currentAssignment.setItSystem(userRole.getItSystem());
		currentAssignment.setOrgUnit(orgunit);
		currentAssignment.setAssignmentId(assignment.getId());
		currentAssignment.setResponsibleOrgUnit(attestationResponsibleOrgUnit);
		currentAssignment.setAssignedBy(assignment.getAssignedByName() + " (" + assignment.getAssignedByUserId() + ")");
		if (assignment.getContainsTitles() != null && assignment.getContainsTitles() != ContainsTitles.NO) {
			currentAssignment.setTitle(title);
		}
		currentAssignment.setManager(assignment.isManager());
		currentAssignment.setSubstitutes(assignment.isSubstitutes());
		currentAssignment.setPostponedConstraints(Set.of());
		currentAssignment.setConstraintSignature(buildConstraintSignature(userRole));
		currentAssignment.setRecordHash(currentAssignment.generateRecordHash());
		return currentAssignment;
	}

	public static Set<CurrentAssignment> toCurrentAssignment(OrgUnitRoleGroupAssignment assignment, User user, OrgUnit positionOrgUnit) {
		Set<CurrentAssignment> currentAssignments = new HashSet<>();

		OrgUnit orgunit = assignment.getOrgUnit();
		RoleGroup roleGroup = assignment.getRoleGroup();

		for (RoleGroupUserRoleAssignment userRoleAssignment : roleGroup.getUserRoleAssignments()) {
			UserRole userRole = userRoleAssignment.getUserRole();

			CurrentAssignment currentAssignment = new CurrentAssignment();
			currentAssignment.setCreatedAt(LocalDateTime.now());
			currentAssignment.setUpdatedAt(LocalDateTime.now());
			currentAssignment.setStartDate(assignment.getStartDate());
			currentAssignment.setStopDate(assignment.getStopDate());
			currentAssignment.setUser(user);
			currentAssignment.setUserRole(userRole);
			currentAssignment.setItSystem(userRole.getItSystem());
			currentAssignment.setOrgUnit(orgunit);
			currentAssignment.setRoleGroup(roleGroup);
			currentAssignment.setAssignmentId(assignment.getId());
			currentAssignment.setResponsibleOrgUnit(positionOrgUnit);
			currentAssignment.setAssignedBy(assignment.getAssignedByName() + " (" + assignment.getAssignedByUserId() + ")");
			currentAssignment.setManager(assignment.isManager());
			currentAssignment.setSubstitutes(assignment.isSubstitutes());

			currentAssignment.setPostponedConstraints(Set.of());
			currentAssignment.setConstraintSignature(buildConstraintSignature(userRole));
			currentAssignment.setRecordHash(currentAssignment.generateRecordHash());
			currentAssignments.add(currentAssignment);
		}

		if (currentAssignments.isEmpty()) {
			// a role group with no userroles must still be represented so the assignment stays visible
			currentAssignments.add(roleGroupOnlyAssignment(roleGroup, user, assignment.getId(),
				assignment.getStartDate(), assignment.getStopDate(),
				assignment.getAssignedByName() + " (" + assignment.getAssignedByUserId() + ")",
				null, orgunit, positionOrgUnit));
		}
		return currentAssignments;
	}

	/**
	 * Builds the single "roleGroup-only" current assignment for a role group with no userroles, so the
	 * assignment is still represented (userRole/itSystem stay null - see {@link CurrentAssignment#isRoleGroupOnly()}).
	 */
	private static CurrentAssignment roleGroupOnlyAssignment(RoleGroup roleGroup, User user, long assignmentId,
			LocalDate startDate, LocalDate stopDate, String assignedBy, String caseNumber, OrgUnit orgUnit,
			OrgUnit responsibleOrgUnit) {
		CurrentAssignment currentAssignment = new CurrentAssignment();
		currentAssignment.setCreatedAt(LocalDateTime.now());
		currentAssignment.setUpdatedAt(LocalDateTime.now());
		currentAssignment.setStartDate(startDate);
		currentAssignment.setStopDate(stopDate);
		currentAssignment.setUser(user);
		currentAssignment.setRoleGroup(roleGroup);
		currentAssignment.setAssignmentId(assignmentId);
		currentAssignment.setOrgUnit(orgUnit);
		currentAssignment.setResponsibleOrgUnit(responsibleOrgUnit);
		currentAssignment.setAssignedBy(assignedBy);
		currentAssignment.setCaseNumber(caseNumber);
		currentAssignment.setPostponedConstraints(Set.of());
		currentAssignment.setConstraintSignature(buildConstraintSignature(null));
		currentAssignment.setRecordHash(currentAssignment.generateRecordHash());
		return currentAssignment;
	}

	private static String buildConstraintSignature(UserRole userRole) {
		if (userRole == null || userRole.getSystemRoleAssignments() == null) {
			return "";
		}
		return userRole.getSystemRoleAssignments().stream()
			.filter(sra -> sra.getConstraintValues() != null && !sra.getConstraintValues().isEmpty())
			.flatMap(sra -> sra.getConstraintValues().stream()
				.filter(cv -> !cv.isPostponed() && cv.getConstraintValue() != null)
				.map(cv -> sra.getSystemRole().getIdentifier()
					+ "|" + cv.getConstraintType().getEntityId()
					+ "|" + cv.getConstraintValue()))
			.sorted()
			.collect(Collectors.joining(","));
	}

	private static CurrentAssignmentPostponedConstraint toCurrentAssignmentPostponedConstraint(PostponedConstraint postponedConstraint, CurrentAssignment currentAssignment) {
		if (postponedConstraint == null || currentAssignment == null) {
			return null;
		}
		CurrentAssignmentPostponedConstraint calcPostponedConstraint = new CurrentAssignmentPostponedConstraint();
		calcPostponedConstraint.setValue(Arrays.stream(postponedConstraint.getValue().split(",")).toList());
		calcPostponedConstraint.setConstraintTypeName(postponedConstraint.getConstraintType().getName());
		calcPostponedConstraint.setConstraintTypeEntityId(postponedConstraint.getConstraintType().getEntityId());
		calcPostponedConstraint.setConstraintTypeUuid(postponedConstraint.getConstraintType().getUuid());
		calcPostponedConstraint.setConstraintTypeId(postponedConstraint.getConstraintType().getId());
		calcPostponedConstraint.setConstraintTypeUIType(postponedConstraint.getConstraintType().getUiType());
		calcPostponedConstraint.setSystemRoleId(postponedConstraint.getSystemRole().getId());
		calcPostponedConstraint.setCurrentAssignment(currentAssignment);
		return calcPostponedConstraint;
	}


}
