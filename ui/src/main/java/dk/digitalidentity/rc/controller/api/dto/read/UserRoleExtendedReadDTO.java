package dk.digitalidentity.rc.controller.api.dto.read;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserRoleExtendedReadDTO {
	private long id;
	private String name;
	private String identifier;
	private String description;
	private String itSystemName;
}
