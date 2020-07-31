package dk.digitalidentity.rc.controller.api.dto.read;

import lombok.Data;

@Data
public class UserRoleReadDTO {
	private long id;
	private String name;
	private String itSystemName;
}
