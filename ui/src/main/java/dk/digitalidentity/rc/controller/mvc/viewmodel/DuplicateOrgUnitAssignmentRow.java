package dk.digitalidentity.rc.controller.mvc.viewmodel;

public record DuplicateOrgUnitAssignmentRow(String ouName, String ouUuid, String roleName, String itSystemName,
		boolean inherit, String filters, String duplicateOf) {
}
