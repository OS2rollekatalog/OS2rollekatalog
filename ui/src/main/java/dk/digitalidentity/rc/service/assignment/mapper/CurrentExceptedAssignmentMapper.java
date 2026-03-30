package dk.digitalidentity.rc.service.assignment.mapper;

import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.OrgUnitRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.OrgUnitUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.RoleGroupUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.Title;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.assignment.CurrentExceptedAssignment;
import dk.digitalidentity.rc.dao.model.enums.ContainsTitles;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

public abstract class CurrentExceptedAssignmentMapper {


	public static CurrentExceptedAssignment toAssignmentException(User user, Title title, OrgUnitUserRoleAssignment assignmentException, OrgUnit positionOrgUnit) {
		CurrentExceptedAssignment currentExceptedAssignment = toAssignmentException(user, assignmentException.getUserRole(), null, assignmentException.getOrgUnit(), ContainsTitles.NO.equals(assignmentException.getContainsTitles()) ? null : title, assignmentException.getId(), assignmentException.getAssignedByName(), assignmentException.getAssignedByUserId(), positionOrgUnit);

		currentExceptedAssignment.setRecordHash(currentExceptedAssignment.generateRecordHash());
		return currentExceptedAssignment;
	}

	public static Set<CurrentExceptedAssignment> toAssignmentException(User user, Title title, OrgUnitRoleGroupAssignment assignmentException, OrgUnit positionOrgUnit) {
		Set<CurrentExceptedAssignment> assignmentExceptions = new HashSet<>();
		for (RoleGroupUserRoleAssignment assignment : assignmentException.getRoleGroup().getUserRoleAssignments()) {
			CurrentExceptedAssignment currentExceptedAssignment = toAssignmentException(user, assignment.getUserRole(), assignment.getRoleGroup(), assignmentException.getOrgUnit(), ContainsTitles.NO.equals(assignmentException.getContainsTitles()) ? null : title, assignmentException.getId(), assignmentException.getAssignedByName(), assignmentException.getAssignedByUserId(), positionOrgUnit);

			currentExceptedAssignment.setRecordHash(currentExceptedAssignment.generateRecordHash());
			assignmentExceptions.add(currentExceptedAssignment);
		}
		return assignmentExceptions;
	}

	private static CurrentExceptedAssignment toAssignmentException(User user, UserRole userRole, RoleGroup roleGroup, OrgUnit orgUnit, Title title, long assignmentId, String assignedByName, String assignedByUserId, OrgUnit positionOrgUnit) {
		CurrentExceptedAssignment currentExceptedAssignment = new CurrentExceptedAssignment();
		currentExceptedAssignment.setExceptionAssignmentId(assignmentId);
		currentExceptedAssignment.setExceptionUserUuid(user.getUuid());
		currentExceptedAssignment.setExceptionUserRoleId(userRole.getId());
		currentExceptedAssignment.setExceptionUserRoleName(userRole.getName());
		currentExceptedAssignment.setExceptionUserRoleDescription(userRole.getDescription());
		currentExceptedAssignment.setExceptionItSystemId(userRole.getItSystem().getId());
		currentExceptedAssignment.setExceptionItSystemName(userRole.getItSystem().getName());
		currentExceptedAssignment.setExceptionOuUuid(orgUnit.getUuid());
		currentExceptedAssignment.setExceptionOuName(orgUnit.getName());
		currentExceptedAssignment.setExceptionTitleUuid(title != null ? title.getUuid() : null);
		currentExceptedAssignment.setExceptionTitleName(title != null ? title.getName() : null);
		currentExceptedAssignment.setExceptionRoleGroupId(roleGroup != null ? roleGroup.getId() : null);
		currentExceptedAssignment.setExceptionRoleGroupName(roleGroup != null ? roleGroup.getName() : null);
		currentExceptedAssignment.setExceptionRoleGroupDescription(roleGroup != null ? roleGroup.getDescription() : null);
		currentExceptedAssignment.setResponsibleOUName(positionOrgUnit != null ? positionOrgUnit.getName() : null);
		currentExceptedAssignment.setResponsibleOUUuid(positionOrgUnit != null ? positionOrgUnit.getUuid() : null);
		currentExceptedAssignment.setAssignedBy(assignedByName + " (" + assignedByUserId + ")");
		currentExceptedAssignment.setCreatedAt(LocalDateTime.now());

		currentExceptedAssignment.setRecordHash(currentExceptedAssignment.generateRecordHash());
		return currentExceptedAssignment;
	}

}
