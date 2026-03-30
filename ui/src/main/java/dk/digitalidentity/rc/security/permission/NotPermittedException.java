package dk.digitalidentity.rc.security.permission;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Custom exception intended to be used when a user does not have the correct Permission to access a section of the app
 */
@Slf4j
@Getter
public class NotPermittedException extends RuntimeException {
	private static final long serialVersionUID = 1L;
	
	private final String permission;
	private final String entity;
	private final boolean constraintMismatched;

	public NotPermittedException(String msg, Section entity, Permission permission) {
		this(msg, entity, permission, false);
	}

	public NotPermittedException(String msg, Section entity, Permission permission, boolean constraintMismatched) {
		super(msg);
		this.permission = permission.name();
		this.entity = entity.name();
		this.constraintMismatched = constraintMismatched;
		log.warn("User was denied acces for permission '{}' with section '{}'", permission.name(), entity, this);
	}
}
