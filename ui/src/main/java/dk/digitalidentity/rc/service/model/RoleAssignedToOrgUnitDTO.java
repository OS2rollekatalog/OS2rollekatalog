package dk.digitalidentity.rc.service.model;

import java.time.LocalDate;

import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.OrgUnitRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.OrgUnitUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.RoleGroupUserRoleAssignment;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RoleAssignedToOrgUnitDTO {	
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
	
	// This is different than the other similar code
	// -2 inherit
	// -1 everyone
	//  0 contains excepted users
	//  1 assigned to 1+ titles
	private int assignmentType = 0;

	public static RoleAssignedToOrgUnitDTO fromRoleGroupUserRoleAssignment(RoleGroupUserRoleAssignment assignment, RoleAssignedToOrgUnitDTO roleGroupAssignment) {
		RoleAssignedToOrgUnitDTO dto = new RoleAssignedToOrgUnitDTO();
		dto.setAssignmentId(assignment.getId());
		dto.setRoleId(assignment.getUserRole().getId());
		dto.setName(assignment.getUserRole().getName());
		dto.setAssignmentType(roleGroupAssignment.getAssignmentType());
		dto.setType(RoleAssignmentType.USERROLE);
		dto.setAssignedThrough(AssignedThrough.ROLEGROUP);
		dto.setAssignedThroughName(assignment.getRoleGroup().getName());
		dto.setItSystem(assignment.getUserRole().getItSystem());
		dto.setDescription(assignment.getUserRole().getDescription());
		dto.setStartDate(roleGroupAssignment.getStartDate());
		dto.setStopDate(roleGroupAssignment.getStopDate());
		dto.setCanEdit(false);
		
		return dto;
	}

	public static RoleAssignedToOrgUnitDTO fromUserRoleAssignment(OrgUnitUserRoleAssignment assignment) {
		RoleAssignedToOrgUnitDTO dto = new RoleAssignedToOrgUnitDTO();
		dto.setAssignmentId(assignment.getId());
		dto.setRoleId(assignment.getUserRole().getId());
		dto.setName(assignment.getUserRole().getName());
		dto.setType(RoleAssignmentType.USERROLE);
		dto.setItSystem(assignment.getUserRole().getItSystem());
		dto.setDescription(assignment.getUserRole().getDescription());
		dto.setCanEdit(false);
		dto.setStartDate(assignment.getStartDate());
		dto.setStopDate(assignment.getStopDate());
		dto.setAssignedThrough(AssignedThrough.DIRECT);
		
		if (assignment.isContainsTitles()) {
			dto.setAssignmentType(assignment.getTitles().size());// assigned through titles
		}
		else if (assignment.isContainsExceptedUsers()) {
			dto.setAssignmentType(0);// Assigned to all with exceptions
		}
		else {
			dto.setAssignmentType(assignment.isInherit() ? -2 : -1);// Assigned to all or with inheritance
		}
		
		return dto;
	}

	public static RoleAssignedToOrgUnitDTO fromUserRoleAssignmentIndirect(OrgUnitUserRoleAssignment assignment) {
		RoleAssignedToOrgUnitDTO dto = new RoleAssignedToOrgUnitDTO();
		dto.setAssignmentId(assignment.getId());
		dto.setRoleId(assignment.getUserRole().getId());
		dto.setName(assignment.getUserRole().getName());
		dto.setType(RoleAssignmentType.USERROLE);
		dto.setAssignedThrough(AssignedThrough.ORGUNIT);
		dto.setAssignedThroughName(assignment.getOrgUnit().getName());
		dto.setItSystem(assignment.getUserRole().getItSystem());
		dto.setDescription(assignment.getUserRole().getDescription());
		dto.setCanEdit(false);
		dto.setStartDate(assignment.getStartDate());
		dto.setStopDate(assignment.getStopDate());
		
		if (assignment.isContainsTitles()) {
			dto.setAssignmentType(assignment.getTitles().size());// assigned through titles
		}
		else if (assignment.isContainsExceptedUsers()) {
			dto.setAssignmentType(0);// Assigned to all with exceptions
		}
		else {
			dto.setAssignmentType(assignment.isInherit() ? -2 : -1);// Assigned to all or with inheritance
		}
		return dto;
	}

	public static RoleAssignedToOrgUnitDTO fromRoleGroupAssignment(OrgUnitRoleGroupAssignment assignment) {
		RoleAssignedToOrgUnitDTO dto = new RoleAssignedToOrgUnitDTO();
		dto.setAssignmentId(assignment.getId());
		dto.setRoleId(assignment.getRoleGroup().getId());
		dto.setName(assignment.getRoleGroup().getName());
		dto.setType(RoleAssignmentType.ROLEGROUP);
		dto.setDescription(assignment.getRoleGroup().getDescription());
		dto.setCanEdit(false);
		dto.setStartDate(assignment.getStartDate());
		dto.setStopDate(assignment.getStopDate());
		dto.setAssignedThrough(AssignedThrough.DIRECT);
		
		if (assignment.isContainsTitles()) {
			dto.setAssignmentType(assignment.getTitles().size());// assigned through titles
		}
		else if (assignment.isContainsExceptedUsers()) {
			dto.setAssignmentType(0);// Assigned to all with exceptions
		}
		else {
			dto.setAssignmentType(assignment.isInherit() ? -2 : -1);// Assigned to all or with inheritance
		}
		
		return dto;
	}

	public static RoleAssignedToOrgUnitDTO fromRoleGroupAssignmentIndirect(OrgUnitRoleGroupAssignment assignment) {
		RoleAssignedToOrgUnitDTO dto = new RoleAssignedToOrgUnitDTO();
		dto.setAssignmentId(assignment.getId());
		dto.setRoleId(assignment.getRoleGroup().getId());
		dto.setName(assignment.getRoleGroup().getName());
		dto.setType(RoleAssignmentType.ROLEGROUP);
		dto.setAssignedThrough(AssignedThrough.ORGUNIT);
		dto.setAssignedThroughName(assignment.getOrgUnit().getName());
		dto.setDescription(assignment.getRoleGroup().getDescription());
		dto.setCanEdit(false);
		dto.setStartDate(assignment.getStartDate());
		dto.setStopDate(assignment.getStopDate());
		
		if (assignment.isContainsTitles()) {
			dto.setAssignmentType(assignment.getTitles().size());// assigned through titles
		}
		else if (assignment.isContainsExceptedUsers()) {
			dto.setAssignmentType(0);// Assigned to all with exceptions
		}
		else {
			dto.setAssignmentType(assignment.isInherit() ? -2 : -1);// Assigned to all or with inheritance
		}
		
		return dto;
	}

}