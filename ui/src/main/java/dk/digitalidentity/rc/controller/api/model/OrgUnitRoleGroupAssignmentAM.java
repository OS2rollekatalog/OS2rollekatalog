package dk.digitalidentity.rc.controller.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@Schema(
	name = "RoleGroupAssignment",
	description = "Tildeling af en rollegruppe til en organisationsenhed"
)
public class OrgUnitRoleGroupAssignmentAM extends BaseOrgUnitAssignmentAM {
	@Schema(
		description = "Rollegruppe der tildeles",
		requiredMode = Schema.RequiredMode.REQUIRED
	)
	@NotNull
	private RoleGroupShallowAM roleGroup;

	public OrgUnitRoleGroupAssignmentAM(RoleGroupShallowAM roleGroup) {
		this.roleGroup = roleGroup;
		setAssignmentType("ROLE_GROUP");
	}
}
