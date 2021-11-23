package dk.digitalidentity.rc.service.model;

import java.time.LocalDate;

import dk.digitalidentity.rc.dao.model.User;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserAssignedToUserRoleDTO {
	private User user;
	private AssignedThrough assignedThrough;
	private long assignmentId;
	private LocalDate startDate;
	private LocalDate stopDate;
	private boolean canEdit;
}
