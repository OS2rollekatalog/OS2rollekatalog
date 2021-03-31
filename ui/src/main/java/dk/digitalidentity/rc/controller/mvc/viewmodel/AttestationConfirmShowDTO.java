package dk.digitalidentity.rc.controller.mvc.viewmodel;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AttestationConfirmShowDTO {
	private String userOrUnitName;
	private String roleName;
	private String itSystemName;
	private boolean roleGroup = false;
	private boolean title = false;
}
