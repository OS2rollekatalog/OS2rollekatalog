package dk.digitalidentity.rc.controller.mvc.viewmodel;

import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.UserRole;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserRoleNotAssignedDTO {
	private String name;
	private String itSystemName;
	private String ouName;

	public UserRoleNotAssignedDTO(UserRole userRole, OrgUnit ou) {
		this.name = userRole.getName();
		this.itSystemName = userRole.getItSystem().getName();
		this.ouName = ou.getName();
	}
}
