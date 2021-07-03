package dk.digitalidentity.rc.controller.mvc.viewmodel;

import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.service.model.RoleAssignedToUser;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class AttestationRolesDTO {
	private User user;
	private RoleAssignedToUser roleAssignedToUser;
	private String roleType;
	private List<String> exceptedUsers;
	
	// set to true for personal roles that cannot be removed attestation
	private boolean disabled;
	
	public String getUserPositionName() {
		if (user != null && user.getPositions().size() > 0) {
			return user.getPositions().get(0).getName() + " i " + user.getPositions().get(0).getOrgUnit().getName();
		}

		return "";
	}
}
