package dk.digitalidentity.rc.service.model;

import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.Title;
import lombok.Getter;
import lombok.Setter;

// This class represents a RoleGroup assignment on a User, either DIRECT or INDIRECT

@Setter
public class RoleGroupAssignmentWithInfo {
	
	@Getter
	private RoleGroup roleGroup;

	// can be NULL if assigned DIRECT or through POSITION
	@Getter
	private AssignedThroughInfo assignedThroughInfo;
	
	// internal use only, should not be exposed through Getters, but can be SET
	private OrgUnit orgUnit;
	private Title title;
	
	@Getter
	private Long assignmentId;

	@Getter
	private boolean fromPosition;

	public RoleGroupAssignedToUser toRoleGroupAssignment() {
		RoleGroupAssignedToUser rgatu = new RoleGroupAssignedToUser();
		rgatu.setRoleGroup(roleGroup);
		rgatu.setTitle(title);
		rgatu.setOrgUnit(orgUnit);
		rgatu.setAssignmentId(assignmentId);
		rgatu.setFromPosition(fromPosition);
		
		if (assignedThroughInfo == null) {
			rgatu.setAssignedThrough(AssignedThrough.DIRECT);
		}
		else {
			switch (assignedThroughInfo.getEntityType()) {
				case ORGUNIT_DIRECT:
					rgatu.setAssignedThrough(AssignedThrough.ORGUNIT);
					break;
				case ORGUNIT_TITLE:
					rgatu.setAssignedThrough(AssignedThrough.TITLE);
					break;
				case ROLEGROUP_DIRECT:
					rgatu.setAssignedThrough(AssignedThrough.ROLEGROUP);
					break;
			}
		}

		return rgatu;
	}
}
