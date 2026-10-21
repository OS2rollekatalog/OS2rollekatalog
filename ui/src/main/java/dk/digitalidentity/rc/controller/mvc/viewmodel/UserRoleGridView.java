package dk.digitalidentity.rc.controller.mvc.viewmodel;

public record UserRoleGridView (
		long id,
		String name,
		String description,
		AvailableITSystemDTO itSystem,
		String itSystemName,
		boolean alreadyAssigned,
		boolean assignable,
		boolean allowPostponing
) {
}
