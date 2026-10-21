package dk.digitalidentity.rc.service.model;

import java.time.LocalDate;
import java.util.List;

/**
 * A row on the users tab of a user role, holding the texts as they are displayed.
 * A label is null when it should not be shown.
 */
public record RoleHolderDTO(
	String name,
	String disabledLabel,
	String outweighedLabel,
	String userId,
	String assignedThrough,
	List<String> validity,
	List<String> positions,
	Edit edit) {

	/** What the edit and delete actions need to act on the assignment */
	public record Edit(
		long assignmentId,
		String userUuid,
		String roleName,
		String orgUnitUuid,
		String caseNumber,
		LocalDate startDate,
		LocalDate stopDate,
		boolean canEdit) {
	}
}
