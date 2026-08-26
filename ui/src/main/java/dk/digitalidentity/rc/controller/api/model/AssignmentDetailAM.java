package dk.digitalidentity.rc.controller.api.model;

import dk.digitalidentity.rc.service.assignment.model.AssignmentType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * En enkelt tildeling set fra rollens side: hvem har den, og hvor kommer den fra.
 * <p>
 * Bruges af både rollegruppe- og jobfunktionsrolle-endpointet, så de to svarer i samme format.
 * Bemærk at skemanavnet ikke må være "RoleGroupAssignment" - det er taget af
 * {@link OrgUnitRoleGroupAssignmentAM}, som er request-modellen for at oprette en tildeling.
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(
	name = "AssignmentDetail",
	description = "En tildeling af en rolle eller rollegruppe til en bruger, inklusive hvor tildelingen stammer fra"
)
public class AssignmentDetailAM {

	@Schema(
		description = "Brugeren der har tildelingen",
		requiredMode = Schema.RequiredMode.REQUIRED
	)
	private UserShallowAM user;

	@Schema(
		description = "Organisationsenheden tildelingen er foretaget på. Er null når rollen er tildelt brugeren direkte"
	)
	private OrgUnitShallowAM orgUnit;

	@Schema(
		description = "Organisationsenheden der er ansvarlig for tildelingen"
	)
	private OrgUnitShallowAM responsibleOrgUnit;

	@Schema(
		description = "Stillingen tildelingen er afgrænset til. Sættes kun for jobfunktionsroller tildelt via "
			+ "stillingskryds (assignedThrough = TITLE). Er altid null for rollegruppe-tildelinger, da "
			+ "stillingsafgrænsningen ikke bæres med over på de beregnede rækker for rollegrupper"
	)
	private TitleAM assignedThroughTitle;

	@Schema(
		description = "Rollegruppen tildelingen kommer igennem. På /rolegroup/{id}/assignments er den altid sat "
			+ "til den forespurgte rollegruppe. På /userrole/{id}/assignments er den kun sat når "
			+ "jobfunktionsrollen er nedarvet gennem en rollegruppe (assignedThrough = ROLE_GROUP)"
	)
	private RoleGroupShallowAM roleGroup;

	// Genbruger enum'en fra UserUserRoleAssignmentAM, så de to endpoints rapporterer
	// samme værdier for samme oprindelse.
	@Schema(
		description = "Hvordan brugeren har fået tildelingen. På /rolegroup/{id}/assignments kan værdien kun være "
			+ "DIRECT eller ORG_UNIT",
		requiredMode = Schema.RequiredMode.REQUIRED
	)
	private UserUserRoleAssignmentAM.AssignedThrough assignedThrough;

	@Schema(
		description = "Id på den underliggende tildeling. Id'et er kun unikt inden for sin assignmentType, så de to "
			+ "felter skal læses sammen. For OU_USER_ROLE og OU_ROLE_GROUP kan id'et bruges direkte mod PUT/DELETE "
			+ "på /organisation/assignment-endpointerne; for de to USER-typer findes der ikke et tilsvarende "
			+ "endpoint der slår op på id",
		example = "123",
		requiredMode = Schema.RequiredMode.REQUIRED
	)
	private long assignmentId;

	@Schema(
		description = "Hvilken slags tildeling assignmentId peger på. Nødvendig for at kunne bruge assignmentId, "
			+ "da de fire typer har hver sin id-serie",
		requiredMode = Schema.RequiredMode.REQUIRED
	)
	private AssignmentType assignmentType;

	@Schema(
		description = "Startdato for hvornår tildelingen trådte i kraft",
		example = "2026-01-01"
	)
	private LocalDate startDate;

	@Schema(
		description = "Slutdato for hvornår tildelingen ophører",
		example = "2026-12-31"
	)
	private LocalDate stopDate;
}
