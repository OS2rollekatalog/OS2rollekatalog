package dk.digitalidentity.rc.controller.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@Schema(
	name = "UserRoleAssignment",
	description = "Tildeling af en jobfunktionsrolle til en organisationsenhed"
)
public class OrgUnitUserRoleAssignmentAM extends BaseOrgUnitAssignmentAM {
	@Schema(
		description = "Jobfunktionsrolle der tildeles",
		requiredMode = Schema.RequiredMode.REQUIRED
	)
	private UserRoleShallowAM userRole;

	public OrgUnitUserRoleAssignmentAM(UserRoleShallowAM userRole) {
		this.userRole = userRole;
		setAssignmentType("USER_ROLE");
	}

	@Schema(
		description = "Type af tildeling",
		requiredMode = Schema.RequiredMode.REQUIRED,
		allowableValues = {"USER_ROLE"},
		example = "USER_ROLE"
	)
	@Override
	public String getAssignmentType() {
		return super.getAssignmentType();
	}
}
