package dk.digitalidentity.rc.controller.api.model.assignmentscopes;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@Schema(
	name = "FunctionScope",
	description = "Regel baseret på funktioner"
)
public class FunctionScopeAM extends AssignmentScopeAM {
	@Schema(
		description = "Liste af funktioner som tildelingen gælder for",
		requiredMode = Schema.RequiredMode.REQUIRED,
		example = "[\"Tillidsmand\", \"Leder\"]"
	)
	@NotNull
	private Set<String> functions;

	public FunctionScopeAM(Set<String> functions) {
		super("FUNCTION");
		this.functions = functions;
	}
}
