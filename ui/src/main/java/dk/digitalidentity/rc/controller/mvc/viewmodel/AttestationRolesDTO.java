package dk.digitalidentity.rc.controller.mvc.viewmodel;

import dk.digitalidentity.rc.dao.model.Position;
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
	private String orgUnitUuid;
	private User user;
	private RoleAssignedToUser roleAssignedToUser;
	private String roleType;
	private List<String> exceptedUsers;
	
	//For postponed constraints
	private List<SystemRoleAssignmentDTO> systemRoleAssignmentsDTOs;
	private Long assignmentId;
	
	// set to true for personal roles that cannot be removed attestation
	private boolean disabled;
	
	private boolean fromPosition;
	
	private boolean checked;
	
	public String getUserPositionName() {
		if (user != null && user.getPositions().size() > 0) {
			if (orgUnitUuid != null) {
				Position position = user.getPositions().stream().filter(p -> orgUnitUuid.equals(p.getOrgUnit().getUuid())).findFirst().orElse(null);
				if (position == null) {
					position = user.getPositions().get(0);
				}
				
				return position.getName() + " i " + position.getOrgUnit().getName();
			} else {
				return user.getPositions().get(0).getName() + " i " + user.getPositions().get(0).getOrgUnit().getName();
			}
		}

		return "";
	}
}
