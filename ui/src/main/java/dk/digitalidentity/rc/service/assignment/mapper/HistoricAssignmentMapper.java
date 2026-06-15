package dk.digitalidentity.rc.service.assignment.mapper;

import dk.digitalidentity.rc.dao.model.assignment.CurrentAssignment;
import dk.digitalidentity.rc.dao.model.assignment.CurrentAssignmentPostponedConstraint;
import dk.digitalidentity.rc.dao.model.assignment.HistoricAssignment;
import dk.digitalidentity.rc.dao.model.assignment.HistoricAssignmentConstraint;
import dk.digitalidentity.rc.service.model.AssignedThrough;

import java.util.stream.Collectors;

public abstract class HistoricAssignmentMapper {

	/**
	 * Returns one {@link HistoricAssignment} per attestation responsible when the assignment routes to
	 * the IT-system attestation pipeline (see {@link #isItSystemResponsible}); otherwise returns a
	 * single OU-attested record. Each per-responsible record gets the same {@code recordHash}
	 * (sourced from {@link CurrentAssignment#getRecordHash()}) — closing by hash invalidates all
	 * fanned-out rows together, which matches "the underlying assignment is gone."
	 */
	public static HistoricAssignment createFromCurrentAssignment(CurrentAssignment currentAssignment, Long responsibleCollectionId) {
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

		boolean isItSystemResponsible = isItSystemResponsible(currentAssignment) && responsibleCollectionId != null;
		String responsibleOuUuid = resolveResponsibleOuUuid(currentAssignment, isItSystemResponsible);
		String responsibleOuName = resolveResponsibleOuName(currentAssignment, isItSystemResponsible);
		Long collectionId = isItSystemResponsible ? responsibleCollectionId : null;

		final AssignedThrough finalAssignedThroughType = assignedThroughtype;
		final String finalAssignedThroughOUId = assignedThroughOUId;
		final String finalAssignedThroughOUName = assignedThroughOUName;
		final String finalAssignedThroughTitleId = assignedThroughTitleId;
		final String finalAssignedThroughTitleName = assignedThroughTitleName;
		final Long finalAssignedThroughRoleGroupId = assignedThroughRoleGroupId;
		final String finalAssignedThroughRoleGroupName = assignedThroughRoleGroupName;

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
			.assignedThroughType(finalAssignedThroughType)
			.assignedThroughTitleUuid(finalAssignedThroughTitleId)
			.assignedThroughTitleName(finalAssignedThroughTitleName)
			.assignedThroughOUUuid(finalAssignedThroughOUId)
			.assignedThroughOUName(finalAssignedThroughOUName)
			.assignedThroughRoleGroupId(finalAssignedThroughRoleGroupId)
			.assignedThroughRoleGroupName(finalAssignedThroughRoleGroupName)

			.responsibleCollectionId(collectionId)
			.responsibleOUName(responsibleOuName)
			.responsibleOUUuid(responsibleOuUuid)

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
	 *   <li>The IT-system has at least one attestation responsible</li>
	 * </ol>
	 * When this returns true, {@code responsibleCollectionId} is set and {@code responsibleOUUuid} is null,
	 * routing the assignment to an IT-system attestation. When false, the opposite applies and the
	 * assignment routes to an organisation attestation.
	 */
	private static boolean isItSystemResponsible(CurrentAssignment ca) {
		return ca.getRoleGroup() == null
			&& ca.getUserRole().isRoleAssignmentAttestationByAttestationResponsible();
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
