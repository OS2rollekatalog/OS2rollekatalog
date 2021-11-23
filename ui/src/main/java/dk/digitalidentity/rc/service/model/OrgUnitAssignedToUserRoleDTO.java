package dk.digitalidentity.rc.service.model;

import java.time.LocalDate;

import dk.digitalidentity.rc.dao.model.OrgUnit;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrgUnitAssignedToUserRoleDTO {
	private OrgUnit orgUnit;
	private long assignmentId;
	private AssignedThrough assignedThrough;
	// This is different than the other similar code
	// -2 inherit
	// -1 everyone
	// 0 contains excepted users
	// 1 assigned to 1+ titles
	private int assignmentType = 0;
	private LocalDate startDate;
	private LocalDate stopDate;
	private boolean canEdit;
	private long roleId;
}
