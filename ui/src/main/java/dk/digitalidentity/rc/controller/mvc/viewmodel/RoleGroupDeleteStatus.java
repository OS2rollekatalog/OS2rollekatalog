package dk.digitalidentity.rc.controller.mvc.viewmodel;

import lombok.Data;

@Data
public class RoleGroupDeleteStatus {
	private boolean success;
	private long ous;
	private long users;
}
