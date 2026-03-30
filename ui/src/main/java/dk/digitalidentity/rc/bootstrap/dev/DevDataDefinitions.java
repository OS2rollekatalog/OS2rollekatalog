package dk.digitalidentity.rc.bootstrap.dev;

import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.dao.model.enums.ConstraintUIType;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import dk.digitalidentity.rc.dao.model.enums.RoleType;
import jakarta.annotation.Nullable;

import java.util.List;

/**
 * Single source of truth for all dev seed data.
 * Each entry point for the dev bootstrap reads from here.
 */
public final class DevDataDefinitions {

	private DevDataDefinitions() {}

	// -------------------------------------------------------------------------
	// Org structure
	// -------------------------------------------------------------------------

	public record OrgUnitDef(String name, List<OrgUnitDef> children) {}

	public static final OrgUnitDef ROOT =
		new OrgUnitDef("Hørning Kommune", List.of(
			new OrgUnitDef("Job og beskæftigelse", List.of(
				new OrgUnitDef("Arbejdsmarkedsområdet", List.of()),
				new OrgUnitDef("Jobcenteret", List.of())
			)),
			new OrgUnitDef("Børn og skole", List.of(
				new OrgUnitDef("Bakkeskolen", List.of()),
				new OrgUnitDef("Aaskolen", List.of())
			)),
			new OrgUnitDef("Teknik og kultur", List.of(
				new OrgUnitDef("Kultur og fritid", List.of()),
				new OrgUnitDef("Teknik og miljø", List.of(
					new OrgUnitDef("Landbrug og natur", List.of())
				))
			))
		));

	// -------------------------------------------------------------------------
	// Titles
	// -------------------------------------------------------------------------

	public static final List<String> TITLE_NAMES = List.of(
		"Sagsbehandler", "Konsulent", "Leder", "Specialist", "Fuldmægtig",
		"Koordinator", "Rådgiver", "Udvikler", "Arkitekt", "Projektleder",
		"Teamleder", "Sekretær", "Direktør", "Vicedirektør", "Chef",
		"Analytiker", "Supporter", "Driftstekniker", "Underviser", "Socialrådgiver"
	);

	// -------------------------------------------------------------------------
	// IT systems
	// -------------------------------------------------------------------------

	public record ConstraintTypeDef(String entityId, String name, String regex, ConstraintUIType uiType) {}

	public record SystemRoleDef(String identifier, String name, RoleType roleType) {}

	public record ItSystemDef(
		String identifier,
		String name,
		ItSystemType systemType,
		boolean canEditThroughApi,
		boolean hasDomain,
		List<SystemRoleDef> roles
	) {}

	public static final List<ConstraintTypeDef> KOMBIT_CONSTRAINT_TYPES = List.of(
		new ConstraintTypeDef(Constants.KLE_CONSTRAINT_ENTITY_ID, "KLE", ".*", ConstraintUIType.REGEX),
		new ConstraintTypeDef(Constants.OU_CONSTRAINT_ENTITY_ID, "Organisation", ".*", ConstraintUIType.REGEX),
		new ConstraintTypeDef(Constants.KOMBIT_ITSYSTEM_CONSTRAINT_ENTITY_ID, "Organisation", ".*", ConstraintUIType.REGEX)
	);

	public static final List<ConstraintTypeDef> NEMLOGIN_CONSTRAINT_TYPES = List.of(
		new ConstraintTypeDef(Constants.PNUMBER_CONSTRAINT_ENTITY_ID, "KLE", ".*", ConstraintUIType.COMBO_SINGLE),
		new ConstraintTypeDef(Constants.SENUMBER_CONSTRAINT_ENTITY_ID, "Organisation", ".*", ConstraintUIType.COMBO_SINGLE)
	);

	public static final List<ItSystemDef> IT_SYSTEMS = List.of(
		new ItSystemDef("AD", "AD", ItSystemType.AD, true, true, List.of(
			new SystemRoleDef("testgroup-001", "AD Group 1", RoleType.BOTH),
			new SystemRoleDef("testgroup-002", "AD Group 2", RoleType.BOTH),
			new SystemRoleDef("testgroup-003", "AD Group 3", RoleType.BOTH),
			new SystemRoleDef("testgroup-004", "AD Group 4", RoleType.BOTH),
			new SystemRoleDef("testgroup-005", "AD Group 5", RoleType.BOTH)
		))
	);

	// -------------------------------------------------------------------------
	// Personas — user + positions + role assignments in one place
	// -------------------------------------------------------------------------

	public record PositionDef(String name, String orgUnitName, @Nullable String titleName) {
		public PositionDef(String name, String orgUnitName) {
			this(name, orgUnitName, null);
		}
	}

	/**
	 * Represents a named dev user together with their positions and system role assignments.
	 *
	 * @param roleKeys Internal aliases resolved at seed time:
	 *                 "administrator" → userRole with identifier "administrator"
	 *                 "assigner"      → userRole with identifier "tildeler"
	 *                 "read-only"     → userRole with identifier "readonly"
	 *                 Empty list means no role assignment for this user.
	 */
	public record PersonaDef(String name, String userId, List<PositionDef> positions, List<String> roleKeys) {}

	public static final List<PersonaDef> PERSONAS = List.of(
		new PersonaDef("Brian Tester", "bsg",
			List.of(new PositionDef("Tester", "Hørning Kommune")),
			List.of("administrator")),
		new PersonaDef("Kaspar Tester", "kbp",
			List.of(new PositionDef("Tester", "Hørning Kommune")),
			List.of("administrator")),
		new PersonaDef("Sune Koch Rønnow", "skr",
			List.of(new PositionDef("Udvikler", "Hørning Kommune", "Sagsbehandler")),
			List.of("administrator")),
		new PersonaDef("Julius Larsen Seerup", "jls",
			List.of(new PositionDef("Udvikler", "Hørning Kommune", "Sagsbehandler")),
			List.of("administrator")),
		new PersonaDef("Piotr Suski", "psu",
			List.of(new PositionDef("Udvikler", "Hørning Kommune", "Sagsbehandler")),
			List.of("administrator")),
		new PersonaDef("Jeremy Leon Gulow", "jgu",
			List.of(new PositionDef("Udvikler", "Hørning Kommune", "Sagsbehandler")),
			List.of("administrator")),
		new PersonaDef("Amalie Bojsen", "abo",
			List.of(new PositionDef("Udvikler", "Hørning Kommune", "Sagsbehandler")),
			List.of("administrator")),
		new PersonaDef("Friis Anton Jensen", "frj",
			List.of(new PositionDef("Udvikler", "Hørning Kommune", "Sagsbehandler")),
			List.of("administrator")),
		new PersonaDef("Test Admin", "test-admin",
			List.of(new PositionDef("Administrator", "Teknik og miljø")),
			List.of("administrator")),
		new PersonaDef("Read Only User", "read-only-user",
			List.of(new PositionDef("Bruger", "Teknik og miljø")),
			List.of("read-only")),
		new PersonaDef("Test Assigner", "test-assigner",
			List.of(new PositionDef("Rolletildeler", "Teknik og miljø")),
			List.of("assigner")),
		new PersonaDef("Test Manager", "test-manager",
			List.of(new PositionDef("Leder", "Teknik og miljø")),
			List.of())    // intentionally no role
	);

	// -------------------------------------------------------------------------
	// IT system responsible
	// -------------------------------------------------------------------------

	/** userId of the user set as attestationResponsible on all IT systems. */
	public static final String IT_SYSTEM_RESPONSIBLE_USER_ID = "test-admin";

	// -------------------------------------------------------------------------
	// Manager overrides
	// -------------------------------------------------------------------------

	public record ManagerAssignment(String userId, String orgUnitName) {}

	public static final List<ManagerAssignment> MANAGER_ASSIGNMENTS = List.of(
		new ManagerAssignment("test-manager", "Teknik og miljø")
	);

}
