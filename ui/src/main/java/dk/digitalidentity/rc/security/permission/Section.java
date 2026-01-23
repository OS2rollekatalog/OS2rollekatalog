package dk.digitalidentity.rc.security.permission;

/**
 * Models a section of the application that requires Permission to access.
 * Could, and should, be replaced with interface-based alternative sometime in the future
 */
public enum Section {
	ROLE_GROUP,
	USER_ROLE,
	IT_SYSTEM,
	ORGUNIT,
	USER,
	REPORT,
	LOG,
	ADVISE,
	MANAGER,
	CONFIG,
	ATTESTATION
}
