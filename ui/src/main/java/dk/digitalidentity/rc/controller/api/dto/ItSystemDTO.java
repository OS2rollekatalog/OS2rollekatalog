package dk.digitalidentity.rc.controller.api.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ItSystemDTO {
	long id;
	String name;
	String identifier;

	public ItSystemDTO(long id, String name, String identifier) {
		this.id = id;
		this.name = name;
		this.identifier = identifier;
	}
}
