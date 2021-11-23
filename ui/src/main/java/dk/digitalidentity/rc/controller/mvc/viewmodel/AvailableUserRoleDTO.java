package dk.digitalidentity.rc.controller.mvc.viewmodel;

import dk.digitalidentity.rc.dao.model.ItSystem;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AvailableUserRoleDTO {
	private long id;
	private String name;
	private String description;
	private ItSystem itSystem;

}
