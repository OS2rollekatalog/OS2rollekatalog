package dk.digitalidentity.rc.controller.mvc.viewmodel;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserAssignStatus {
	private boolean success;
	private boolean alreadyAssignedThroughOu;
	private boolean alreadyAssignedThroughRoleGroup;
	private boolean alreadyAssignedThroughTitle;
}
