package dk.digitalidentity.rc.config;

public class Constants {
	public static final String ROLE_CATALOGUE_IDENTIFIER = "RoleCatalogue";

	// Spring Security constants
	public static final String ROLE_ATTESTATION_ADMINISTRATOR = "ROLE_ATTESTATION_ADMINISTRATOR";
	public static final String ROLE_ADMINISTRATOR = "ROLE_ADMINISTRATOR";
	public static final String ROLE_REPORT_ACCESS = "ROLE_REPORT_ACCESS";
	public static final String ROLE_USER_ASSIGNER = "ROLE_USER_ASSIGNER";
	public static final String ROLE_OU_ASSIGNER = "ROLE_OU_ASSIGNER";
	public static final String ROLE_READ_ACCESS = "ROLE_READ_ACCESS";
	public static final String ROLE_KLE_ADMINISTRATOR = "ROLE_KLE_ADMINISTRATOR";
	public static final String ROLE_SYSTEM = "ROLE_SYSTEM";
	public static final String ROLE_REQUESTER = "ROLE_REQUESTER";
	public static final String ROLE_MANAGER = "ROLE_MANAGER";
	public static final String ROLE_SUBSTITUTE = "ROLE_SUBSTITUTE";
	public static final String ROLE_MANAGER_SUBSTITUDE = "ROLE_MANAGER_SUBSTITUDE";
	public static final String ROLE_TEMPLATE_ACCESS = "ROLE_TEMPLATE_ACCESS";
	public static final String ROLE_IT_SYSTEM_RESPONSIBLE = "ROLE_IT_SYSTEM_RESPONSIBLE";
	public static final String ROLE_AUDITLOG = "ROLE_AUDITLOG";


	// role catalogue IDs for assignables roles
	public static final String ROLE_ADMINISTRATOR_ID = "http://rollekatalog.dk/administrator";
	public static final String ROLE_USER_ASSIGNER_ID = "http://rollekatalog.dk/assigner/user";
	public static final String ROLE_OU_ASSIGNER_ID = "http://rollekatalog.dk/assigner/orgunit";
	public static final String ROLE_GLOBAL_ASSIGNER_ID = "http://rollekatalog.dk/global_assigner";
	public static final String ROLE_READ_ACCESS_ID = "http://rollekatalog.dk/readaccess";
	public static final String ROLE_KLE_ADMINISTRATOR_ID = "http://rollekatalog.dk/kleadmin";
	public static final String ROLE_ATTESTATION_ADMINISTRATOR_ID = "http://rollekatalog.dk/attestationadmin";
	public static final String ROLE_REPORT_ACCESS_ID = "http://rollekatalog.dk/reportaccess";
	public static final String ROLE_REQUESTAUTHORIZED = "http://rollekatalog.dk/requestauthorized";

	public static final String ROLE_USERROLE_READ_ID = "http://rollekatalog.dk/userrole/read";
	public static final String ROLE_USERROLE_UPDATE_ID = "http://rollekatalog.dk/userrole/update";
	public static final String ROLE_USERROLE_CREATE_ID = "http://rollekatalog.dk/userrole/create";
	public static final String ROLE_USERROLE_DELETE_ID = "http://rollekatalog.dk/userrole/delete";

	public static final String ROLE_ROLEGROUP_READ_ID = "http://rollekatalog.dk/rolegroup/read";
	public static final String ROLE_ROLEGROUP_UPDATE_ID = "http://rollekatalog.dk/rolegroup/update";
	public static final String ROLE_ROLEGROUP_CREATE_ID = "http://rollekatalog.dk/rolegroup/create";
	public static final String ROLE_ROLEGROUP_DELETE_ID = "http://rollekatalog.dk/rolegroup/delete";

	public static final String ROLE_ITSYSTEM_READ_ID = "http://rollekatalog.dk/itsystem/read";
	public static final String ROLE_ITSYSTEM_UPDATE_ID = "http://rollekatalog.dk/itsystem/update";
	public static final String ROLE_ITSYSTEM_CREATE_ID = "http://rollekatalog.dk/itsystem/create";
	public static final String ROLE_ITSYSTEM_DELETE_ID = "http://rollekatalog.dk/itsystem/delete";

	public static final String ROLE_OU_READ_ID = "http://rollekatalog.dk/orgunit/read";
	public static final String ROLE_OU_UPDATE_ID = "http://rollekatalog.dk/orgunit/update";
	public static final String ROLE_USER_READ_ID = "http://rollekatalog.dk/user/read";
	public static final String ROLE_USER_UPDATE_ID = "http://rollekatalog.dk/user/update";

	public static final String ROLE_LOG_READ_ID = "http://rollekatalog.dk/log/read";
	public static final String ROLE_ADVISE_READ_ID = "http://rollekatalog.dk/advise/read";
	public static final String ROLE_MANAGER_READ_ID = "http://rollekatalog.dk/manager/read";
	public static final String ROLE_MANAGER_UPDATE_ID = "http://rollekatalog.dk/manager/update";

	public static final String ROLE_CONFIG_READ_ID = "http://rollekatalog.dk/config/read";


	// KOMBIT ID's for special constraints handled by the UI
	public static final String KLE_CONSTRAINT_ENTITY_ID = "http://sts.kombit.dk/constraints/KLE/1";
	public static final String OU_CONSTRAINT_ENTITY_ID = "http://sts.kombit.dk/constraints/orgenhed/1";
	public static final String KOMBIT_ITSYSTEM_CONSTRAINT_ENTITY_ID = "http://sts.kombit.dk/constraints/itsystem/1";

	// internal constraints in role catalogue
	public static final String INTERNAL_ITSYSTEM_CONSTRAINT_ENTITY_ID = "http://digital-identity.dk/constraints/itsystem/1";
	public static final String INTERNAL_ORGUNIT_CONSTRAINT_ENTITY_ID = "http://digital-identity.dk/constraints/orgunit/1";

	// NemLogin constraint entity IDs
	public static final String PNUMBER_CONSTRAINT_ENTITY_ID = "https://nemlogin.dk/constraints/pnr/1";
	public static final String SENUMBER_CONSTRAINT_ENTITY_ID = "https://nemlogin.dk/constraints/senr/1";
}
