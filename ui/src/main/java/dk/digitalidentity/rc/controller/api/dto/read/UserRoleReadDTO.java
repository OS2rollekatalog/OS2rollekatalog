package dk.digitalidentity.rc.controller.api.dto.read;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserRoleReadDTO {
	private long id;
	private String name;
	private String itSystemName;
	private String description;
}
