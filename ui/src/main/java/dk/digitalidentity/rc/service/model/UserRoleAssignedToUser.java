package dk.digitalidentity.rc.service.model;

import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.Title;
import dk.digitalidentity.rc.dao.model.UserRole;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserRoleAssignedToUser implements RoleAssignedToUser {
	private UserRole userRole;
	private AssignedThrough assignedThrough;
	private Title title;
	private OrgUnit orgUnit;

	@Override
	public long getRoleId() {
		if (userRole != null) {
			return userRole.getId();
		}

		return 0;
	}

	@Override
	public String getTitleOrOrgUnitUuid() {
		if (title != null || orgUnit != null) {
			if (assignedThrough.equals(AssignedThrough.TITLE)) {
				return title.getUuid();
			}
			else if (assignedThrough.equals(AssignedThrough.ORGUNIT)) {
				return orgUnit.getUuid();
			}
		}

		return "";
	}

	@Override
	public String getTitleOrOrgUnitName() {
		if (title != null || orgUnit != null) {
			if (assignedThrough.equals(AssignedThrough.TITLE)) {
				return title.getName();
			}
			else if (assignedThrough.equals(AssignedThrough.ORGUNIT)) {
				return orgUnit.getName();
			}
		}

		return "";
	}
}
