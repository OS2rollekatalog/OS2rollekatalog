package dk.digitalidentity.rc.controller.api.dto.read;

import dk.digitalidentity.rc.dao.model.PostponedConstraint;
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
}
