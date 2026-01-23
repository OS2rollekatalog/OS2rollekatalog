package dk.digitalidentity.rc.security.permission;

/**
 * Models permission rights.
 * Users have permissions for specific sections/entities of the app, and sections of the app require specific permissions.
 */
public enum Permission {
	CREATE,
	READ,
	UPDATE,
	DELETE
}
