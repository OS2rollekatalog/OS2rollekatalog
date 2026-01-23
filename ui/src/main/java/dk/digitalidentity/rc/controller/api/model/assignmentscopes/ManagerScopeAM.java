package dk.digitalidentity.rc.controller.api.model.assignmentscopes;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(
	name = "ManagerScope",
	description = "Regel som gør det muligt at kun managers og eller stedfortrædere bliver omfattet af tildelingen"
)
public class ManagerScopeAM extends AssignmentScopeAM {
	@Schema(example = "true", description = "Gælder tildelingen for managers")
	private boolean manager;
	@Schema(example = "true", description = "Gælder tildelingen for stedfortrædere")
	private boolean substitute;

	public ManagerScopeAM() {
		setType("MANAGER");
	}
}
