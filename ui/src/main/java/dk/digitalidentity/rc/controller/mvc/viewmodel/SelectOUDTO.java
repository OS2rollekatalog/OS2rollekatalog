package dk.digitalidentity.rc.controller.mvc.viewmodel;

import dk.digitalidentity.rc.dao.model.OrgUnit;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SelectOUDTO {
	private String uuid;
	private String name;

	public SelectOUDTO(OrgUnit orgUnit) {
		this.uuid = orgUnit.getUuid();
		this.name = orgUnit.getName();
	}
}
