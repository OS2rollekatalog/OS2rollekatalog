package dk.digitalidentity.rc.controller.mvc.viewmodel;

import dk.digitalidentity.rc.dao.model.RoleGroup;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EditRolegroupRow {
	private RoleGroup roleGroup;
	private boolean checked;
}
