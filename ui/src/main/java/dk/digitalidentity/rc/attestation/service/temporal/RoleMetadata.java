package dk.digitalidentity.rc.attestation.service.temporal;

import java.util.Map;

/** Current name and description of a role; the historic tables only carry the copy frozen when their row was written. */
public record RoleMetadata(String name, String description) {

	public static String nameOf(final Map<Long, RoleMetadata> metadata, final Long roleId, final String frozen) {
		final RoleMetadata current = roleId != null ? metadata.get(roleId) : null;
		return current != null ? current.name() : frozen;
	}

	public static String descriptionOf(final Map<Long, RoleMetadata> metadata, final Long roleId, final String frozen) {
		final RoleMetadata current = roleId != null ? metadata.get(roleId) : null;
		return current != null ? current.description() : frozen;
	}

}
