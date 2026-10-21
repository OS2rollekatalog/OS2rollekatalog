package dk.digitalidentity.rc.rolerequest.service.permission;

import lombok.Getter;

import java.util.List;

@Getter
public enum RequestAuthority {
	NONE("en som ikke burde have adgang"),
	ADMIN("en administrator"),
	REQUEST_AUTHORIZED("en bemyndiget"),
	AUTH_RESPONSIBLE("en autorisationsansvarlig"),
	MANAGER("en leder"),
	SELF("medarbejderen selv");

	public static final List<RequestAuthority> BY_PRECEDENCE = List.of(ADMIN, REQUEST_AUTHORIZED, SELF, MANAGER, AUTH_RESPONSIBLE);

	private final String message;

	RequestAuthority(String message) {
		this.message = message;
	}
}
