package dk.digitalidentity.rc.service.model;

import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.Title;
import dk.digitalidentity.rc.dao.model.UserRole;
import lombok.Getter;
import lombok.Setter;

// This class represents a UserRole assignment on a User, either DIRECT or INDIRECT

@Setter
public class UserRoleAssignmentWithInfo {
	
	@Getter
	private UserRole userRole;

	// can be NULL if assigned DIRECT or through POSITION
	@Getter
	private AssignedThroughInfo assignedThroughInfo;
	
	// internal use only, should not be exposed through Getters, but can be SET
	private OrgUnit orgUnit;
	private Title title;

	public UserRoleAssignedToUser toUserRoleAssignedToUser() {
		UserRoleAssignedToUser uratu = new UserRoleAssignedToUser();
		uratu.setUserRole(userRole);
		uratu.setTitle(title);
		uratu.setOrgUnit(orgUnit);
		
		if (assignedThroughInfo == null) {
			uratu.setAssignedThrough(AssignedThrough.DIRECT);
		}
		else {
			switch (assignedThroughInfo.getEntityType()) {
				case ORGUNIT_DIRECT:
					uratu.setAssignedThrough(AssignedThrough.ORGUNIT);
					break;
				case ORGUNIT_TITLE:
					uratu.setAssignedThrough(AssignedThrough.TITLE);
					break;
				case ROLEGROUP_DIRECT:
					uratu.setAssignedThrough(AssignedThrough.ROLEGROUP);
					break;
			}
		}

		return uratu;
	}
}
