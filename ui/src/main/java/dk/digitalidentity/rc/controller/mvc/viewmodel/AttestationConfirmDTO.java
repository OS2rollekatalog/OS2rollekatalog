package dk.digitalidentity.rc.controller.mvc.viewmodel;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AttestationConfirmDTO {
	private String aprovedUnit;
	private String aprovedPersonal;
	private String toBeRemoved;
	private String toEmail;
	private String orgUnitUuid;
	private String adAproved;
	private String adNotAproved;
}
