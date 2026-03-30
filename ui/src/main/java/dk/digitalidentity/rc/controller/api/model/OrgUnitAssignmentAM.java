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
	name = "SystemRoleAssignment",
	description = "Tildeling af en rolle eller rollegruppe til en organisationsenhed. " +
		"Bemærk: Angiv enten roleGroup ELLER userRole - aldrig begge samtidig.",
	oneOf = {UserRoleShallowAM.class, RoleGroupShallowAM.class}
)
public class OrgUnitAssignmentAM extends BaseOrgUnitAssignmentAM {

	@Schema(
		description = "Jobfunktionsrolle der tildeles (brug ENTEN denne eller rollebuket)",
		nullable = true
	)
	private UserRoleShallowAM userRole;
	public OrgUnitAssignmentAM(final UserRoleShallowAM userRole) {
		this.userRole = userRole;
		setAssignmentType("USER_ROLE");
	}
}
