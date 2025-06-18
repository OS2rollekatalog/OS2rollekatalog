package dk.digitalidentity.rc.controller.mvc.viewmodel;

import dk.digitalidentity.rc.dao.model.RoleGroupUserRoleAssignment;
import dk.digitalidentity.rc.rolerequest.model.enums.ApproverOption;
import dk.digitalidentity.rc.rolerequest.model.enums.RequesterOption;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
	@Size(min = 2, max = 64, message="{validation.rolegroup.name}")
	private String name;

	private boolean userOnly;

	private RequesterOption requesterPermission = RequesterOption.NONE;

	private ApproverOption approverPermission = ApproverOption.ADMINONLY;

	@Size(max = 4000)
	private String description;

	private List<RoleGroupUserRoleAssignment> userRoleAssignments;
}
