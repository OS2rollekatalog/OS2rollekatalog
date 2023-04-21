package dk.digitalidentity.rc.controller.rest.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ManagerSubstituteMappingDTO {
	private String substitute;
	private String orgUnit;
	private String manager;
}
