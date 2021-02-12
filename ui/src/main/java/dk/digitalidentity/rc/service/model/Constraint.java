package dk.digitalidentity.rc.service.model;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class Constraint {
	private String type;
	private String parameter;
	private String value;
}
