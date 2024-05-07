package dk.digitalidentity.rc.controller.viewmodel;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SystemRoleForm {
	private long id;
	private String description;
	private long itSystemId;

	@Size(min = 3, max = 128)
	private String name;

	@Size(min = 3, max = 128)
	private String identifier;
}
