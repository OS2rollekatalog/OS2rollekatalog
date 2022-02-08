package dk.digitalidentity.rc.controller.mvc.viewmodel;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AttestationConfirmADDTO {
	private String userUuid;
	private String userUserId;
	private String userName;
}
