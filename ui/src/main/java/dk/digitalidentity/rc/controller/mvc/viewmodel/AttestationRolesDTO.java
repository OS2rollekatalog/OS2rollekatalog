package dk.digitalidentity.rc.controller.mvc.viewmodel;

import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.service.model.RoleAssignedToUser;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AttestationRolesDTO {
	private User user;
	private RoleAssignedToUser roleAssignedToUser;
	private String roleType;
	
	// set to true for personal roles that cannot be removed attestation
	private boolean disabled;
}
