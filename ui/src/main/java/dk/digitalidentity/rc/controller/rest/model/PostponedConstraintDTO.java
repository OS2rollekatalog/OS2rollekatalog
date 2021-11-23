package dk.digitalidentity.rc.controller.rest.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PostponedConstraintDTO {
	private String type;
	private long systemRoleId;
	private String constraintTypeUuid;
	private String value;
}
