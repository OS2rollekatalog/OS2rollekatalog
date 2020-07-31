package dk.digitalidentity.rc.controller.rest.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserHistoryDTO {
	private String timestamp;
	private String username;
	private String eventType;
	private String roleName;
	private String systemName;
}
