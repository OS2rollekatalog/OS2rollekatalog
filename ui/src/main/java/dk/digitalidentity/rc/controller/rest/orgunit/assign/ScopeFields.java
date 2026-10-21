package dk.digitalidentity.rc.controller.rest.orgunit.assign;

import java.util.Set;

/**
 * Possible fields that the Ou Assign wizard can contain
 */
public record ScopeFields(
		Set<String> exceptedOuUuids,
		Set<String> exceptedUserUuids,
		Set<String> titleUuids,
		Set<String> functionUuids,
		boolean includeSubstitutes) {

	private static final ScopeFields EMPTY = new ScopeFields(Set.of(), Set.of(), Set.of(), Set.of(), false);

	public static ScopeFields empty() {
		return EMPTY;
	}
}
