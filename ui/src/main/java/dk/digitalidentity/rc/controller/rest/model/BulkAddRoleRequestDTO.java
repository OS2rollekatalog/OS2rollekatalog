package dk.digitalidentity.rc.controller.rest.model;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class BulkAddRoleRequestDTO {
	private List<UserAssignmentDTO> userAssignments;
	private String startDate;
	private String stopDate;
	private String caseNumber;
	private List<PostponedConstraintDTO> postponedConstraints;

	@Getter
	@Setter
	public static class UserAssignmentDTO {
		private String userUuid;
		private String orgUnitUuid;
	}
}
