package dk.digitalidentity.rc.controller.mvc.viewmodel;

public record UserRoleDTO (
		long id,
		String name,
		String description,
		AvailableITSystemDTO itSystem,
		boolean alreadyAssigned
) {
}
