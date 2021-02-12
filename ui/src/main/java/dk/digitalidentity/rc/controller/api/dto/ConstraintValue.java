package dk.digitalidentity.rc.controller.api.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ConstraintValue {
	private String constraintType;
	private String[] constraintValues;
}
