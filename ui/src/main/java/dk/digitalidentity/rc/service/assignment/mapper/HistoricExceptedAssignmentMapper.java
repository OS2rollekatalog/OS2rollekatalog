package dk.digitalidentity.rc.service.assignment.mapper;

import dk.digitalidentity.rc.dao.model.assignment.CurrentExceptedAssignment;
import dk.digitalidentity.rc.dao.model.assignment.HistoricExceptedAssignment;

public abstract class HistoricExceptedAssignmentMapper {

	public static HistoricExceptedAssignment createFromCurrentExceptedAssignment(CurrentExceptedAssignment current) {
		HistoricExceptedAssignment historic = new HistoricExceptedAssignment();

		historic.setRecordHash(current.getRecordHash());
		historic.setExceptionAssignmentId(current.getExceptionAssignmentId());

		historic.setExceptionUserUuid(current.getExceptionUserUuid());
		historic.setExceptionUserRoleId(current.getExceptionUserRoleId());
		historic.setExceptionUserRoleName(current.getExceptionUserRoleName());
		historic.setExceptionUserRoleDescription(current.getExceptionUserRoleDescription());

		historic.setExceptionRoleGroupId(current.getExceptionRoleGroupId());
		historic.setExceptionRoleGroupName(current.getExceptionRoleGroupName());
		historic.setExceptionRoleGroupDescription(current.getExceptionRoleGroupDescription());

		historic.setExceptionItSystemId(current.getExceptionItSystemId());
		historic.setExceptionItSystemName(current.getExceptionItSystemName());

		historic.setExceptionOuUuid(current.getExceptionOuUuid());
		historic.setExceptionOuName(current.getExceptionOuName());

		historic.setExceptionTitleUuid(current.getExceptionTitleUuid());
		historic.setExceptionTitleName(current.getExceptionTitleName());

		historic.setResponsibleOUUuid(current.getResponsibleOUUuid());
		historic.setResponsibleOUName(current.getResponsibleOUName());

		historic.setAssignedBy(current.getAssignedBy());

		historic.setStartDate(current.getStartDate());
		historic.setStopDate(current.getStopDate());

		historic.setValidFrom(current.getCreatedAt());
		historic.setValidTo(null);

		return historic;
	}
}
