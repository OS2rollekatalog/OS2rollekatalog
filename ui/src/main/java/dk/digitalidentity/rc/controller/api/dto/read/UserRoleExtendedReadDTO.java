package dk.digitalidentity.rc.controller.api.dto.read;

import lombok.Data;

@Data
public class UserRoleExtendedReadDTO {
	private long id;
	private String name;
	private String identifier;
	private String description;
	private String itSystemName;
}
