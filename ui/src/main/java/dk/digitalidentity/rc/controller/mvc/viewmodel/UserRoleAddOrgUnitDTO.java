package dk.digitalidentity.rc.controller.mvc.viewmodel;

import dk.digitalidentity.rc.dao.model.OrgUnit;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UserRoleAddOrgUnitDTO {
	private String name;
	private String uuid;

	public UserRoleAddOrgUnitDTO(OrgUnit ou) {
		this.name = ou.getName();
		this.uuid = ou.getUuid();
	}

}
