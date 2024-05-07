package dk.digitalidentity.rc.controller.api.dto;

import dk.digitalidentity.rc.dao.model.SystemRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class SystemRoleDTO {
	private String description;

	@NotBlank
	private String name;

	@Pattern(regexp = "^[A-Za-z0-9_-]+$")
	private String identifier;

	private List<String> users;

	public SystemRoleDTO(SystemRole sr) {
		this.name = sr.getName();
		this.identifier = sr.getIdentifier();
		this.description = sr.getDescription();
	}
}
