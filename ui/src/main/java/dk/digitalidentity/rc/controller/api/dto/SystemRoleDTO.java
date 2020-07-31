package dk.digitalidentity.rc.controller.api.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

import dk.digitalidentity.rc.dao.model.SystemRole;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SystemRoleDTO {
	private String description;
	
	@NotBlank
	private String name;

	@Pattern(regexp = "^[A-Za-z0-9_-]+$")
	private String identifier;

	public SystemRoleDTO(SystemRole sr) {
		this.name = sr.getName();
		this.identifier = sr.getIdentifier();
		this.description = sr.getDescription();
	}
}
