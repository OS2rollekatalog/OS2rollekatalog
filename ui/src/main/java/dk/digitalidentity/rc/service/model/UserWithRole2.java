package dk.digitalidentity.rc.service.model;

import java.util.List;

import dk.digitalidentity.rc.dao.model.Position;
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
	public List<Position> positions;
}
