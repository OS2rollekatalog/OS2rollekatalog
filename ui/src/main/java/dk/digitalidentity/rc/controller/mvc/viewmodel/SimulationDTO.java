package dk.digitalidentity.rc.controller.mvc.viewmodel;

import dk.digitalidentity.rc.controller.model.enums.LoginType;
import dk.digitalidentity.rc.dao.model.ItSystem;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SimulationDTO {
	private String userId;
	private ItSystem itSystem;
	private LoginType loginType;
}
