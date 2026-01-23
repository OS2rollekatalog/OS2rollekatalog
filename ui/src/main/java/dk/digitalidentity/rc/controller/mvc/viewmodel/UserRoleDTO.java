package dk.digitalidentity.rc.controller.mvc.viewmodel;

public record UserRoleDTO (
		long id,
		String name,
		String description,
		String itSystemName,
		String itSystemType,
		boolean alreadyAssigned
) {
}
