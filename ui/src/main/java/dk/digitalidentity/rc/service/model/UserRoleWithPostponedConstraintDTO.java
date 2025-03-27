package dk.digitalidentity.rc.service.model;

import java.util.List;

import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.PostponedConstraint;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.UserUserRoleAssignment;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserRoleWithPostponedConstraintDTO {
	private UserRole userRole;
	private List<PostponedConstraint> postponedConstraints;
	private long assignmentId;
	private OrgUnit orgUnit;

	public UserRoleWithPostponedConstraintDTO(UserUserRoleAssignment userUserRoleAssignment) {
		this.userRole = userUserRoleAssignment.getUserRole();
		this.postponedConstraints = userUserRoleAssignment.getPostponedConstraints();
		this.assignmentId = userUserRoleAssignment.getId();
		this.orgUnit = userUserRoleAssignment.getOrgUnit();
	}
}