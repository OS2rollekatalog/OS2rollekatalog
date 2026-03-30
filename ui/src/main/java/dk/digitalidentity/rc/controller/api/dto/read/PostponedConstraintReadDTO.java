package dk.digitalidentity.rc.controller.api.dto.read;

import dk.digitalidentity.rc.dao.model.PostponedConstraint;
import dk.digitalidentity.rc.dao.model.assignment.CurrentAssignmentPostponedConstraint;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PostponedConstraintReadDTO {
	private String type;
	private long systemRoleId;
	private String value;

	public PostponedConstraintReadDTO(PostponedConstraint postponedConstraint) {
		this.type = postponedConstraint.getConstraintType().getName();
		this.systemRoleId = postponedConstraint.getSystemRole().getId();
		this.value = postponedConstraint.getValue();
	}

	public PostponedConstraintReadDTO(CurrentAssignmentPostponedConstraint postponedConstraint, Long id) {
		this.type = postponedConstraint.getConstraintTypeName();
		this.systemRoleId = id;
		this.value = String.join(",", postponedConstraint.getValue());
	}
}
