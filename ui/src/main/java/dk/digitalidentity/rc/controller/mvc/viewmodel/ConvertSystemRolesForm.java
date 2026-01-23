package dk.digitalidentity.rc.controller.mvc.viewmodel;

import dk.digitalidentity.rc.dao.model.enums.SystemRoleLinkType;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ConvertSystemRolesForm {
	private String prefix;
	private SystemRoleLinkType convertOption;
}
