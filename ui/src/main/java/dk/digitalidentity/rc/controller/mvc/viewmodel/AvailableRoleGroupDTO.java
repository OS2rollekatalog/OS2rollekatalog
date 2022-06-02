package dk.digitalidentity.rc.controller.mvc.viewmodel;

import lombok.Getter;
import lombok.Setter;
@Getter
@Setter
public class AvailableRoleGroupDTO {
	private long id;
	private String name;
	private String description;
	private boolean alreadyAssigned;
}