package dk.digitalidentity.rc.controller.api.model.assignmentscopes;

import dk.digitalidentity.rc.controller.api.model.UserShallowAM;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
@Schema(
	name = "ExceptedUserScope",
	description = "Regel som indeholder undtagne brugere, kan kombineres med TitleScope"
)
public class ExceptedUserScopeAM extends AssignmentScopeAM {
	@Schema(description = "Liste af brugere som er undtaget", requiredMode = Schema.RequiredMode.REQUIRED)
	@NotNull
	@Valid
	private Set<UserShallowAM> exceptedUsers;

	public ExceptedUserScopeAM() {
		setType("EXCEPTED_USER");
	}
}
