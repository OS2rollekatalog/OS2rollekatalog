package dk.digitalidentity.rc.service.model;

import java.time.LocalDate;
import java.util.List;

import dk.digitalidentity.rc.controller.mvc.viewmodel.SystemRoleAssignmentDTO;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.OrgUnitRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.OrgUnitUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.PositionRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.PositionUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.RoleGroupUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.UserRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.UserUserRoleAssignment;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RoleAssignedToUserDTO {
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
	private boolean canEdit;
	private List<SystemRoleAssignmentDTO> systemRoleAssignments;
	private boolean ineffective = false;

	public static RoleAssignedToUserDTO fromRoleGroupUserRoleAssignment(RoleGroupUserRoleAssignment assignment, LocalDate startDate, LocalDate stopDate) {
		RoleAssignedToUserDTO dto = new RoleAssignedToUserDTO();
		dto.setAssignmentId(assignment.getId());
		dto.setRoleId(assignment.getUserRole().getId());
		dto.setName(assignment.getUserRole().getName());
		dto.setType(RoleAssignmentType.USERROLE);
		dto.setAssignedThrough(AssignedThrough.ROLEGROUP);
		dto.setAssignedThroughName(assignment.getRoleGroup().getName());
		dto.setItSystem(assignment.getUserRole().getItSystem());
		dto.setDescription(assignment.getUserRole().getDescription());
		dto.setStartDate(startDate);
		dto.setStopDate(stopDate);
		dto.setCanEdit(false);
		
		return dto;
	}

	public static RoleAssignedToUserDTO fromUserRoleAssignment(UserUserRoleAssignment assignment) {
		RoleAssignedToUserDTO dto = new RoleAssignedToUserDTO();
		dto.setAssignmentId(assignment.getId());
		dto.setRoleId(assignment.getUserRole().getId());
		dto.setName(assignment.getUserRole().getName());
		dto.setType(RoleAssignmentType.USERROLE);
		dto.setAssignedThrough(AssignedThrough.DIRECT);
		dto.setItSystem(assignment.getUserRole().getItSystem());
		dto.setDescription(assignment.getUserRole().getDescription());
		dto.setStartDate(assignment.getStartDate());
		dto.setStopDate(assignment.getStopDate());
		dto.setCanEdit(false);
		
		return dto;
	}

	public static RoleAssignedToUserDTO fromRoleGroupAssignment(UserRoleGroupAssignment assignment) {
		RoleAssignedToUserDTO dto = new RoleAssignedToUserDTO();
		dto.setAssignmentId(assignment.getId());
		dto.setRoleId(assignment.getRoleGroup().getId());
		dto.setName(assignment.getRoleGroup().getName());
		dto.setType(RoleAssignmentType.ROLEGROUP);
		dto.setAssignedThrough(AssignedThrough.DIRECT);
		dto.setDescription(assignment.getRoleGroup().getDescription());
		dto.setStartDate(assignment.getStartDate());
		dto.setStopDate(assignment.getStopDate());
		dto.setCanEdit(false);
		
		return dto;
	}

	public static RoleAssignedToUserDTO fromPositionUserRoleAssignment(PositionUserRoleAssignment assignment) {
		RoleAssignedToUserDTO dto = new RoleAssignedToUserDTO();
		dto.setAssignmentId(assignment.getId());
		dto.setRoleId(assignment.getUserRole().getId());
		dto.setName(assignment.getUserRole().getName());
		dto.setType(RoleAssignmentType.USERROLE);
		dto.setAssignedThrough(AssignedThrough.POSITION);
		dto.setItSystem(assignment.getUserRole().getItSystem());
		dto.setDescription(assignment.getUserRole().getDescription());
		dto.setStartDate(assignment.getStartDate());
		dto.setStopDate(assignment.getStopDate());
		dto.setCanEdit(false);
		
		return dto;
	}

	public static RoleAssignedToUserDTO fromPositionRoleGroupAssignment(PositionRoleGroupAssignment assignment) {
		RoleAssignedToUserDTO dto = new RoleAssignedToUserDTO();
		dto.setAssignmentId(assignment.getId());
		dto.setRoleId(assignment.getRoleGroup().getId());
		dto.setName(assignment.getRoleGroup().getName());
		dto.setType(RoleAssignmentType.ROLEGROUP);
		dto.setAssignedThrough(AssignedThrough.POSITION);
		dto.setDescription(assignment.getRoleGroup().getDescription());
		dto.setStartDate(assignment.getStartDate());
		dto.setStopDate(assignment.getStopDate());
		dto.setCanEdit(false);
		
		return dto;
	}

	public static RoleAssignedToUserDTO fromOrgUnitUserRoleAssignment(OrgUnitUserRoleAssignment assignment) {
		RoleAssignedToUserDTO dto = new RoleAssignedToUserDTO();
		dto.setAssignmentId(assignment.getId());
		dto.setRoleId(assignment.getUserRole().getId());
		dto.setName(assignment.getUserRole().getName());
		dto.setType(RoleAssignmentType.USERROLE);
		
		if (!assignment.isContainsTitles()) {
			dto.setAssignedThrough(AssignedThrough.ORGUNIT);
		}
		else {
			dto.setAssignedThrough(AssignedThrough.TITLE);
		}
		
		dto.setAssignedThroughName(assignment.getOrgUnit().getName());
		dto.setItSystem(assignment.getUserRole().getItSystem());
		dto.setDescription(assignment.getUserRole().getDescription());
		dto.setStartDate(assignment.getStartDate());
		dto.setStopDate(assignment.getStopDate());
		dto.setCanEdit(false);
		
		return dto;
	}

	public static RoleAssignedToUserDTO fromOrgUnitRoleGroupAssignment(OrgUnitRoleGroupAssignment assignment) {
		RoleAssignedToUserDTO dto = new RoleAssignedToUserDTO();
		dto.setAssignmentId(assignment.getId());
		dto.setRoleId(assignment.getRoleGroup().getId());
		dto.setName(assignment.getRoleGroup().getName());
		dto.setType(RoleAssignmentType.ROLEGROUP);
		
		if (!assignment.isContainsTitles()) {
			dto.setAssignedThrough(AssignedThrough.ORGUNIT);
		}
		else {
			dto.setAssignedThrough(AssignedThrough.TITLE);
		}
		
		dto.setAssignedThroughName(assignment.getOrgUnit().getName());
		dto.setDescription(assignment.getRoleGroup().getDescription());
		dto.setStartDate(assignment.getStartDate());
		dto.setStopDate(assignment.getStopDate());
		dto.setCanEdit(false);
		
		return dto;
	}
}
