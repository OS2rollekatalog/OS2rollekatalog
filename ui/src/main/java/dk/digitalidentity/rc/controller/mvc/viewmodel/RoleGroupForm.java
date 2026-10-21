package dk.digitalidentity.rc.controller.mvc.viewmodel;

import dk.digitalidentity.rc.rolerequest.model.permission.ApproverPermission;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.RoleGroupUserRoleAssignment;
import dk.digitalidentity.rc.rolerequest.model.enums.ApprovableBy;
import dk.digitalidentity.rc.rolerequest.model.enums.RequestableBy;
import dk.digitalidentity.rc.rolerequest.model.permission.RequesterPermission;
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

	public static RoleGroupForm from(RoleGroup roleGroup) {
		RoleGroupForm form = new RoleGroupForm();
		form.setId(roleGroup.getId());
		form.setName(roleGroup.getName());
		form.setUserOnly(roleGroup.isUserOnly());
		form.setRequesterPermission(roleGroup.getRequesterPermission().getRequestableBy());
		form.setApproverPermission(roleGroup.getApproverPermission().getApprovableBy());
		form.setDescription(roleGroup.getDescription());
		form.setOuFilterEnabled(roleGroup.isOuFilterEnabled());
		form.setUserRoleAssignments(roleGroup.getUserRoleAssignments());
		return form;
	}

	public RoleGroup toNewRoleGroup() {
		RoleGroup roleGroup = new RoleGroup();
		roleGroup.setName(name);
		roleGroup.setUserOnly(userOnly);
		roleGroup.setRequesterPermission(RequesterPermission.of(requesterPermission));
		roleGroup.setApproverPermission(ApproverPermission.of(approverPermission));
		roleGroup.setDescription(description);
		roleGroup.setOuFilterEnabled(ouFilterEnabled);
		return roleGroup;
	}
}
