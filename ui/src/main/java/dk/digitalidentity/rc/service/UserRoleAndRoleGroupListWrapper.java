package dk.digitalidentity.rc.service;

import java.util.List;

import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.UserRole;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserRoleAndRoleGroupListWrapper {
	private List<UserRole> userRoles;
	private List<RoleGroup> roleGroups;
}
