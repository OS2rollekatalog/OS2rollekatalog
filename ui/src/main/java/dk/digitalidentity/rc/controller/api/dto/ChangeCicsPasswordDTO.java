package dk.digitalidentity.rc.controller.api.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChangeCicsPasswordDTO {
	private String username;
	private String newPassword;
}
