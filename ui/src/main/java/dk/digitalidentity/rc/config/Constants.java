package dk.digitalidentity.rc.config;

public class Constants {
	public static final String ROLE_CATALOGUE_IDENTIFIER = "RoleCatalogue";
	
	// Spring Security constants
	public static final String ROLE_ADMINISTRATOR = "ROLE_ADMINISTRATOR";
	public static final String ROLE_ASSIGNER = "ROLE_ASSIGNER";
	public static final String ROLE_READ_ACCESS = "ROLE_READ_ACCESS";
	public static final String ROLE_KLE_ADMINISTRATOR = "ROLE_KLE_ADMINISTRATOR";
	public static final String ROLE_SYSTEM = "ROLE_SYSTEM";
	public static final String ROLE_REQUESTER = "ROLE_REQUESTER";
	public static final String ROLE_MANAGER = "ROLE_MANAGER";
	public static final String ROLE_SUBSTITUTE = "ROLE_SUBSTITUTE";
	public static final String ROLE_TEMPLATE_ACCESS = "ROLE_TEMPLATE_ACCESS";

	// role catalogue IDs for assignables roles
	public static final String ROLE_ADMINISTRATOR_ID = "http://rollekatalog.dk/administrator";
	public static final String ROLE_ASSIGNER_ID = "http://rollekatalog.dk/assigner";
	public static final String ROLE_READ_ACCESS_ID = "http://rollekatalog.dk/readaccess";
	public static final String ROLE_KLE_ADMINISTRATOR_ID = "http://rollekatalog.dk/kleadmin";

	// KOMBIT ID's for special constraints handled by the UI
	public static final String KLE_CONSTRAINT_ENTITY_ID = "http://sts.kombit.dk/constraints/KLE/1";
	public static final String OU_CONSTRAINT_ENTITY_ID = "http://sts.kombit.dk/constraints/orgenhed/1";
	
	// internal constraints in role catalogue
	public static final String ITSYSTEM_CONSTRAINT_ENTITY_ID = "http://digital-identity.dk/constraints/itsystem/1";
	public static final String ORGUNIT_CONSTRAINT_ENTITY_ID = "http://digital-identity.dk/constraints/orgunit/1";
}
