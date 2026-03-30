package dk.digitalidentity.rc.service.assignment.mapper;

import dk.digitalidentity.rc.dao.model.assignment.CurrentAssignment;
import dk.digitalidentity.rc.dao.model.assignment.CurrentAssignmentPostponedConstraint;
import dk.digitalidentity.rc.dao.model.assignment.HistoricAssignment;
import dk.digitalidentity.rc.dao.model.assignment.HistoricAssignmentConstraint;
import dk.digitalidentity.rc.service.model.AssignedThrough;

import java.util.stream.Collectors;

public abstract class HistoricAssignmentMapper {

	public static HistoricAssignment createFromCurrentAssignment(CurrentAssignment currentAssignment) {
		AssignedThrough assignedThroughtype = AssignedThrough.DIRECT;
		String assignedThroughOUId = null;
		String assignedThroughOUName = null;
		String assignedThroughTitleId = null;
		String assignedThroughTitleName = null;
		Long assignedThroughRoleGroupId = null;
		String assignedThroughRoleGroupName = null;

		if (currentAssignment.getTitle() != null && currentAssignment.getOrgUnit() != null) {
			assignedThroughtype = AssignedThrough.TITLE;
			assignedThroughTitleId = currentAssignment.getTitle().getUuid();
			assignedThroughTitleName = currentAssignment.getTitle().getName();
			assignedThroughOUId = currentAssignment.getOrgUnit().getUuid();
			assignedThroughOUName = currentAssignment.getOrgUnit().getName();
		} else if (currentAssignment.getOrgUnit() != null) {
			assignedThroughtype = AssignedThrough.ORGUNIT;
			assignedThroughOUId = currentAssignment.getOrgUnit().getUuid();
			assignedThroughOUName = currentAssignment.getOrgUnit().getName();
		} else if (currentAssignment.getRoleGroup() != null) {
			assignedThroughtype = AssignedThrough.ROLEGROUP;
			assignedThroughRoleGroupId = currentAssignment.getRoleGroup().getId();
			assignedThroughRoleGroupName = currentAssignment.getRoleGroup().getName();
		}

		Long roleGroupId = currentAssignment.getRoleGroup() != null ? currentAssignment.getRoleGroup().getId() : null;
		String roleGroupname = currentAssignment.getRoleGroup() != null ? currentAssignment.getRoleGroup().getName() : null;
		String roleGroupDescription = currentAssignment.getRoleGroup() != null ? currentAssignment.getRoleGroup().getDescription() : null;

		boolean isItSystemResponsible = isItSystemResponsible(currentAssignment);

		HistoricAssignment historicAssignment = HistoricAssignment.builder()
				.updatedAt(currentAssignment.getUpdatedAt())
				.recordHash(currentAssignment.getRecordHash())
				.startDate(currentAssignment.getStartDate())
				.stopDate(currentAssignment.getStopDate())
				.validFrom(currentAssignment.getCreatedAt())
				.validTo(null)

				.userUuid(currentAssignment.getUser().getUuid())
				.userId(currentAssignment.getUser().getUserId())
				.userName(currentAssignment.getUser().getName())

				.userRoleId(currentAssignment.getUserRole().getId())
				.userRoleName(currentAssignment.getUserRole().getName())
				.userRoleDescription(currentAssignment.getUserRole().getDescription())
				.sensitiveRole(currentAssignment.getUserRole().isSensitiveRole())
				.extraSensitiveRole(currentAssignment.getUserRole().isExtraSensitiveRole())

				.itSystemId(currentAssignment.getItSystem().getId())
				.itSystemName(currentAssignment.getItSystem().getName())

				.roleGroupId(roleGroupId)
				.roleGroupName(roleGroupname)
				.roleGroupDescription(roleGroupDescription)

				.assignedBy(currentAssignment.getAssignedBy())
				.assignedThroughType(assignedThroughtype)
				.assignedThroughTitleUuid(assignedThroughTitleId)
				.assignedThroughTitleName(assignedThroughTitleName)
				.assignedThroughOUUuid(assignedThroughOUId)
				.assignedThroughOUName(assignedThroughOUName)
				.assignedThroughRoleGroupId(assignedThroughRoleGroupId)
				.assignedThroughRoleGroupName(assignedThroughRoleGroupName)

				// responsibleUserUuid and responsibleOUUuid are mutually exclusive — see isItSystemResponsible().
				.responsibleUserUuid(resolveResponsibleUserUuid(currentAssignment, isItSystemResponsible))
				.responsibleOUName(resolveResponsibleOuName(currentAssignment, isItSystemResponsible))
				.responsibleOUUuid(resolveResponsibleOuUuid(currentAssignment, isItSystemResponsible))

				.build();

		historicAssignment.setConstraints(currentAssignment.getPostponedConstraints().stream()
				.map(pc -> fromCurrentPostponedConstraint(pc, historicAssignment))
				.collect(Collectors.toSet()));

		return historicAssignment;
	}

	/**
	 * Determines whether the IT-system's designated attestation responsible should attest this
	 * assignment, rather than the responsible OU's manager.
	 * <p>
	 * This is true when all three conditions hold:
	 * <ol>
	 *   <li>The assignment is not via a role group (role group assignments always go to the OU manager)</li>
	 *   <li>The role has {@code roleAssignmentAttestationByAttestationResponsible = true}</li>
	 *   <li>The IT-system has a non-null {@code attestationResponsible}</li>
	 * </ol>
	 * When this returns true, {@code responsibleUserUuid} is set and {@code responsibleOUUuid} is null,
	 * routing the assignment to an IT-system attestation. When false, the opposite applies and the
	 * assignment routes to an organisation attestation.
	 */
	private static boolean isItSystemResponsible(CurrentAssignment ca) {
		return ca.getRoleGroup() == null
			&& ca.getUserRole().isRoleAssignmentAttestationByAttestationResponsible()
			&& ca.getItSystem().getAttestationResponsible() != null;
	}

	/** Returns the IT-system attestation responsible's UUID, or null if the OU manager is responsible. */
	private static String resolveResponsibleUserUuid(CurrentAssignment ca, boolean isItSystemResponsible) {
		return isItSystemResponsible ? ca.getItSystem().getAttestationResponsible().getUuid() : null;
	}

	/** Returns the responsible OU UUID, or null if the IT-system attestation responsible is responsible instead. */
	private static String resolveResponsibleOuUuid(CurrentAssignment ca, boolean isItSystemResponsible) {
		if (isItSystemResponsible) {
			return null;
		}
		return ca.getResponsibleOrgUnit() != null ? ca.getResponsibleOrgUnit().getUuid() : null;
	}

	/** Returns the responsible OU name, or null if the IT-system attestation responsible is responsible instead. */
	private static String resolveResponsibleOuName(CurrentAssignment ca, boolean isItSystemResponsible) {
		if (isItSystemResponsible) {
			return null;
		}

		return ca.getResponsibleOrgUnit() != null ? ca.getResponsibleOrgUnit().getName() : null;
	}

	private static HistoricAssignmentConstraint fromCurrentPostponedConstraint(CurrentAssignmentPostponedConstraint postponedConstraint, HistoricAssignment historicAssignment) {
		return HistoricAssignmentConstraint.builder()
			.historicAssignment(historicAssignment)
			.constraintTypeUuid(postponedConstraint.getConstraintTypeUuid())
			.constraintTypeName(postponedConstraint.getConstraintTypeName())
			.constraintTypeEntityId(postponedConstraint.getConstraintTypeEntityId())
			.value(postponedConstraint.getValue())
			.build();
	}
}
