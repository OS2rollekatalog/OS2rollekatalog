package dk.digitalidentity.rc.service.model;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class Privilege {
	private String identifier;
	private String name;
}
