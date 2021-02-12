package dk.digitalidentity.rc.controller.api.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ItSystemDTO {
	private long id;
	private String name;
	private String identifier;

	public ItSystemDTO(long id, String name, String identifier) {
		this.id = id;
		this.name = name;
		this.identifier = identifier;
	}
}
