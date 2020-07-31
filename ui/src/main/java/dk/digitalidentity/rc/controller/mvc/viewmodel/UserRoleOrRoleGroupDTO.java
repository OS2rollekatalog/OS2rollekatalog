package dk.digitalidentity.rc.controller.mvc.viewmodel;

import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.UserRole;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserRoleOrRoleGroupDTO {
	private long userRoleId;
	private long roleGroupId;
	private String name;
	private String itSystemName;
	private String description;
	
	// not used by Thymeleaf, only by java code
	private UserRole userRole;
	private RoleGroup roleGroup;

	public UserRoleOrRoleGroupDTO(UserRole userRole) {
		this.userRoleId = userRole.getId();
		this.itSystemName = userRole.getItSystem().getName();
		this.name = userRole.getName();
		this.description = userRole.getDescription();
		this.userRole = userRole;
	}
	
	public UserRoleOrRoleGroupDTO(RoleGroup roleGroup) {
		this.roleGroupId = roleGroup.getId();
		this.name = roleGroup.getName();
		this.description = roleGroup.getDescription();
		this.roleGroup = roleGroup;
	}

	public boolean isUserRole() {
		return (userRoleId > 0);
	}
}
