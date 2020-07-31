package dk.digitalidentity.rc.dao.model.enums;

public enum RoleType {
	DATA_ROLE,			// the role is a data-constrain role, and needs to be combined with a FUNCTION_ROLE
	FUNCTION_ROLE,		// the role only gives access to functions, and needs to be combined with a DATA_ROLE
	BOTH				// these are ordinary roles used by most systems (including all KOMBIT systems)
}
