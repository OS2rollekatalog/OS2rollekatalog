package dk.digitalidentity.rc.controller.api.model.assignmentscopes;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Schema(name = "AssignmentScope", description = "Scope der definerer hvem tildelingen gælder for")
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", visible = true)
@JsonSubTypes({
	@JsonSubTypes.Type(value = TitleScopeAM.class, name = "TITLE"),
	@JsonSubTypes.Type(value = ExcludedTitleScopeAM.class, name = "EXCLUDED_TITLE"),
	@JsonSubTypes.Type(value = FunctionScopeAM.class, name = "FUNCTION"),
	@JsonSubTypes.Type(value = ManagerScopeAM.class, name = "MANAGER"),
	@JsonSubTypes.Type(value = ExceptedUserScopeAM.class, name = "EXCEPTED_USER")
})
public abstract class AssignmentScopeAM {
	@Schema(description = "Type af scope", requiredMode = Schema.RequiredMode.REQUIRED)
	@NotNull
	private String type;
}
