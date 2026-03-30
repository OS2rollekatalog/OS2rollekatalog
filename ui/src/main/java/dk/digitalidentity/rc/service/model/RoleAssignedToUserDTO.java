package dk.digitalidentity.rc.service.model;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import dk.digitalidentity.rc.controller.mvc.viewmodel.SystemRoleAssignmentDTO;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.assignment.CurrentAssignment;
import dk.digitalidentity.rc.dao.model.assignment.CurrentExceptedAssignment;
import dk.digitalidentity.rc.dao.model.enums.ContainsTitles;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RoleAssignedToUserDTO {
	private long currentAssignmentId;
	private long assignmentId;
	private long roleId;
	private String name;
	private RoleAssignmentType type;
	private AssignedThrough assignedThrough;
	private String assignedThroughName;
	private ItSystem itSystem;
	private String description;
	private LocalDate startDate;
	private LocalDate stopDate;
	private ContainsTitles containsTitles = ContainsTitles.NO;
	private List<String> titleUuids = Collections.emptyList();

	// set by caller after creation
	private boolean canEdit;

	// only set in UserController.handleUserRoleOrNegativeRole after creation
	private List<SystemRoleAssignmentDTO> systemRoleAssignments;
	private Integer highestSystemRoleWeight;
	private String ineffectiveReason;
	private boolean ineffective = false;

	// only for directly assigned roles
	private String orgUnitUuid;

	// only for directly assigned user roles
	private String caseNumber;

	public static RoleAssignedToUserDTO fromCurrentAssignmentUserRole(CurrentAssignment assignment, AssignedThrough assignedThrough) {
		RoleAssignedToUserDTO dto = new RoleAssignedToUserDTO();

		// Basic assignment info
		dto.setCurrentAssignmentId(assignment.getId());
		dto.setAssignmentId(assignment.getAssignmentId());
		dto.setRoleId(assignment.getUserRole().getId());
		dto.setName(assignment.getUserRole().getName());
		dto.setType(RoleAssignmentType.USERROLE);
		dto.setItSystem(assignment.getItSystem());
		dto.setDescription(assignment.getUserRole().getDescription());
		dto.setStartDate(assignment.getStartDate());
		dto.setStopDate(assignment.getStopDate());
		dto.setAssignedThrough(assignedThrough);
		dto.setCaseNumber(assignment.getCaseNumber());

		switch (assignedThrough) {
			case ROLEGROUP:
				if (assignment.getRoleGroup() != null) {
					dto.setAssignedThroughName(assignment.getRoleGroup().getName());
				}
				break;
			case ORGUNIT:
				if (assignment.getOrgUnit() != null) {
					dto.setAssignedThroughName(assignment.getOrgUnit().getName());
				}
				break;
			case TITLE:
				if (assignment.getOrgUnit() != null) {
					dto.setAssignedThroughName(assignment.getOrgUnit().getName());
					dto.setContainsTitles(ContainsTitles.POSITIVE);
				}
				break;
			case DIRECT:
				dto.setOrgUnitUuid(assignment.getResponsibleOrgUnit() == null ? null : assignment.getResponsibleOrgUnit().getUuid());
				break;
			case POSITION:
				// not possible anymore
				break;
		}

		if (assignment.getTitle() != null) {
			dto.setTitleUuids(Collections.singletonList(assignment.getTitle().getUuid()));
		}

		dto.setCanEdit(false);

		return dto;
	}

	public static RoleAssignedToUserDTO fromCurrentAssignmentRoleGroup(CurrentAssignment assignment, AssignedThrough assignedThrough) {
		RoleAssignedToUserDTO dto = new RoleAssignedToUserDTO();

		// Basic assignment info
		dto.setCurrentAssignmentId(assignment.getId());
		dto.setAssignmentId(assignment.getAssignmentId());
		dto.setRoleId(assignment.getRoleGroup().getId());
		dto.setName(assignment.getRoleGroup().getName());
		dto.setType(RoleAssignmentType.ROLEGROUP);
		dto.setDescription(assignment.getRoleGroup().getDescription());
		dto.setStartDate(assignment.getStartDate());
		dto.setStopDate(assignment.getStopDate());
		dto.setAssignedThrough(assignedThrough);

		switch (assignedThrough) {
			case ORGUNIT:
				if (assignment.getOrgUnit() != null) {
					dto.setAssignedThroughName(assignment.getOrgUnit().getName());
				}
				break;
			case TITLE:
				if (assignment.getOrgUnit() != null) {
					dto.setAssignedThroughName(assignment.getOrgUnit().getName());
					dto.setContainsTitles(ContainsTitles.POSITIVE);
				}
				break;
			case DIRECT, ROLEGROUP:
				dto.setOrgUnitUuid(assignment.getResponsibleOrgUnit() == null ? null : assignment.getResponsibleOrgUnit().getUuid());
				break;
			case POSITION:
				// not possible anymore
				break;
		}
		dto.setCaseNumber(assignment.getCaseNumber());

		if (assignment.getTitle() != null) {
			dto.setTitleUuids(Collections.singletonList(assignment.getTitle().getUuid()));
		}

		dto.setCanEdit(false);

		return dto;
	}

	public static RoleAssignedToUserDTO fromCurrentExceptedAssignmentUserRole(CurrentExceptedAssignment assignment, ItSystem itSystem) {
		RoleAssignedToUserDTO dto = new RoleAssignedToUserDTO();

		dto.setAssignmentId(assignment.getExceptionAssignmentId());
		dto.setRoleId(assignment.getExceptionUserRoleId());
		dto.setName(assignment.getExceptionUserRoleName());
		dto.setType(RoleAssignmentType.NEGATIVE);
		dto.setItSystem(itSystem);
		dto.setDescription(assignment.getExceptionUserRoleDescription());
		dto.setStartDate(assignment.getStartDate());
		dto.setStopDate(assignment.getStopDate());

		// Determine AssignedThrough based on whether it's title-based or user-based exception
		if (assignment.getExceptionTitleUuid() != null) {
			dto.setAssignedThrough(AssignedThrough.TITLE);
			dto.setAssignedThroughName(assignment.getExceptionOuName());
			dto.setContainsTitles(ContainsTitles.NEGATIVE);
			dto.setTitleUuids(Collections.singletonList(assignment.getExceptionTitleUuid()));
		} else {
			// User-based exception
			dto.setAssignedThrough(AssignedThrough.ORGUNIT);
			dto.setAssignedThroughName(assignment.getExceptionOuName());
		}

		dto.setCanEdit(false);

		return dto;
	}

	public static RoleAssignedToUserDTO fromCurrentExceptedAssignmentRoleGroup(CurrentExceptedAssignment assignment) {
		RoleAssignedToUserDTO dto = new RoleAssignedToUserDTO();

		dto.setAssignmentId(assignment.getExceptionAssignmentId());
		dto.setRoleId(assignment.getExceptionRoleGroupId());
		dto.setName(assignment.getExceptionRoleGroupName());
		dto.setType(RoleAssignmentType.NEGATIVE_ROLEGROUP);
		dto.setDescription(assignment.getExceptionRoleGroupDescription());
		dto.setStartDate(assignment.getStartDate());
		dto.setStopDate(assignment.getStopDate());

		// Determine AssignedThrough based on whether it's title-based or user-based exception
		if (assignment.getExceptionTitleUuid() != null) {
			dto.setAssignedThrough(AssignedThrough.TITLE);
			dto.setAssignedThroughName(assignment.getExceptionOuName());
			dto.setContainsTitles(ContainsTitles.NEGATIVE);
			dto.setTitleUuids(Collections.singletonList(assignment.getExceptionTitleUuid()));
		} else {
			// User-based exception
			dto.setAssignedThrough(AssignedThrough.ORGUNIT);
			dto.setAssignedThroughName(assignment.getExceptionOuName());
		}

		dto.setCanEdit(false);

		return dto;
	}
}

