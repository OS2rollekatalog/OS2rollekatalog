package dk.digitalidentity.rc.controller.mvc.xlsview;

import java.util.List;

import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.UserRole;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ItSystemWithUserRoles {
	private ItSystem itSystem;
	private List<UserRole> userRoles;
}
