package dk.digitalidentity.rc.controller.api.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import dk.digitalidentity.rc.controller.api.model.assignmentscopes.AssignmentScopeAM;
import dk.digitalidentity.rc.controller.api.model.assignmentscopes.ExceptedUserScopeAM;
import dk.digitalidentity.rc.controller.api.model.assignmentscopes.ExcludedTitleScopeAM;
import dk.digitalidentity.rc.controller.api.model.assignmentscopes.FunctionScopeAM;
import dk.digitalidentity.rc.controller.api.model.assignmentscopes.ManagerScopeAM;
import dk.digitalidentity.rc.controller.api.model.assignmentscopes.TitleScopeAM;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Basis for enheds rolletildelinger")
@JsonTypeInfo(
	use = JsonTypeInfo.Id.NAME,
	include = JsonTypeInfo.As.PROPERTY,
	property = "assignmentType",
	visible = true
)
@JsonSubTypes({
	@JsonSubTypes.Type(value = OrgUnitUserRoleAssignmentAM.class, name = "USER_ROLE"),
	@JsonSubTypes.Type(value = OrgUnitRoleGroupAssignmentAM.class, name = "ROLE_GROUP")
})
public abstract class BaseOrgUnitAssignmentAM {

	@Schema(
		description = "Type af tildeling",
		requiredMode = Schema.RequiredMode.REQUIRED,
		allowableValues = {"USER_ROLE", "ROLE_GROUP"}
	)
	@NotNull
	private String assignmentType;

	@Schema(
		description = "Unik id for tildelingen",
		example = "123",
		accessMode = Schema.AccessMode.READ_ONLY
	)
	private Long assignmentId;

	@Schema(
		description = "Angiver om tildelingen skal nedarves til underliggende organisationsenheder",
		example = "true"
	)
	private boolean inherit;

	@Schema(
		description = "Scopes der definerer hvem tildelingen gælder for, bemærk man kan ikke mixe and matche, de eneste der kan kombineres er TitleScopeAM og ExceptedUserScopeAM, hvis der ikke sendes nogle scopes, gælder tildelingen for alle",
		oneOf = {
			TitleScopeAM.class,
			ExcludedTitleScopeAM.class,
			FunctionScopeAM.class,
			ManagerScopeAM.class,
			ExceptedUserScopeAM.class
		}
	)
	@Valid
	private List<AssignmentScopeAM> scopes;

	@Schema(
		description = "Tidspunkt for hvornår tildelingen blev oprettet",
		accessMode = Schema.AccessMode.READ_ONLY,
		example = "2025-11-01T10:30:00"
	)
	private LocalDateTime assignedAt;

	@Schema(
		description = "Navn på den person der oprettede tildelingen",
		accessMode = Schema.AccessMode.READ_ONLY,
		example = "Anders Andersen"
	)
	private String assignedByName;

	@Schema(
		description = "Bruger-ID på den person der oprettede tildelingen",
		accessMode = Schema.AccessMode.READ_ONLY,
		example = "aand"
	)
	private String assignedByUserId;

	@Schema(
		description = "Startdato for hvornår tildelingen træder i kraft",
		example = "2025-11-01"
	)
	private LocalDate startDate;

	@Schema(
		description = "Slutdato for hvornår tildelingen ophører",
		example = "2025-12-31"
	)
	private LocalDate stopDate;

	@Schema(
		description = "Organisationsenhed som rollen/rollegruppen er tildelt til",
		requiredMode = Schema.RequiredMode.REQUIRED
	)
	@NotNull
	private OrgUnitShallowAM orgUnit;
}
