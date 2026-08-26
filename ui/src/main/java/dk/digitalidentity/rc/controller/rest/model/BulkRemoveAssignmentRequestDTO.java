package dk.digitalidentity.rc.controller.rest.model;

import dk.digitalidentity.rc.service.model.RoleAssignmentType;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class BulkRemoveAssignmentRequestDTO {
	private List<AssignmentToRemoveDTO> assignments;

	@Getter
	@Setter
	public static class AssignmentToRemoveDTO {
		private RoleAssignmentType type;
		private long assignmentId;
	}
}
