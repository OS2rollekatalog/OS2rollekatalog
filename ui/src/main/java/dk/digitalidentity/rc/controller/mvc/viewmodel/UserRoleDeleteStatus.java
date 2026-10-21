package dk.digitalidentity.rc.controller.mvc.viewmodel;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class UserRoleDeleteStatus {
	private boolean success;
	private long ous;
	private long users;
	private long roleGroups;
	private List<String> adGroups = List.of();
}
