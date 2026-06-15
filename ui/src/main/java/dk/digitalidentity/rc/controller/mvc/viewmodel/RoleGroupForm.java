package dk.digitalidentity.rc.controller.mvc.viewmodel;

import dk.digitalidentity.rc.dao.model.RoleGroupUserRoleAssignment;
import dk.digitalidentity.rc.rolerequest.model.enums.ApprovableBy;
import dk.digitalidentity.rc.rolerequest.model.enums.RequestableBy;
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
	@Size(min = 2, max = 128, message="{validation.rolegroup.name}")
	private String name;

	private boolean userOnly;

	private List<RequestableBy> requesterPermission = List.of(RequestableBy.INHERIT);

	private List<ApprovableBy> approverPermission = List.of(ApprovableBy.INHERIT);

	@Size(max = 4000)
	private String description;

	private boolean ouFilterEnabled;

	private List<RoleGroupUserRoleAssignment> userRoleAssignments;
}
