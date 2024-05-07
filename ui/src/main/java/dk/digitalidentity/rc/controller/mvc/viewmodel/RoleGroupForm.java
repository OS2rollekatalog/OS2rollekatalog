package dk.digitalidentity.rc.controller.mvc.viewmodel;

import dk.digitalidentity.rc.dao.model.RoleGroupUserRoleAssignment;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class RoleGroupForm {
	private long id;

	@NotNull
	@Size(min = 5, max = 64, message="{validation.rolegroup.name}")
	private String name;

	private boolean userOnly;
		
	private boolean canRequest;

	@Size(max = 4000)
	private String description;

	private List<RoleGroupUserRoleAssignment> userRoleAssignments;
}
