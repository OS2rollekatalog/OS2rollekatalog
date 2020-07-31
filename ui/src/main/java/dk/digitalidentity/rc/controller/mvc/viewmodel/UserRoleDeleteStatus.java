package dk.digitalidentity.rc.controller.mvc.viewmodel;

import lombok.Data;

@Data
public class UserRoleDeleteStatus {
	private boolean success;
	private long ous;
	private long users;
	private long roleGroups;
}
