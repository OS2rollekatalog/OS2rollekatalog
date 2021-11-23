package dk.digitalidentity.rc.service.model;

import lombok.Getter;
import lombok.Setter;

// TODO: rename once we get rid of the old code
@Getter
@Setter
public class UserWithRole2 {
	public String userUuid;
	public String userName;
	public String userUserId;
	public RoleAssignedToUserDTO assignment;
}
