package dk.digitalidentity.rc.controller.mvc.viewmodel;

import java.util.List;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import dk.digitalidentity.rc.dao.model.RoleGroupUserRoleAssignment;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RoleGroupForm {
	private long id;

	@NotNull
	@Size(min = 5, max = 64, message="{validation.rolegroup.name}")
	private String name;

	private boolean userOnly;
	
	private boolean ouInheritAllowed;
	
	private boolean canRequest;

	@Size(max = 4000)
	private String description;

	private List<RoleGroupUserRoleAssignment> userRoleAssignments;
}
