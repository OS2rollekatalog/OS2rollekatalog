package dk.digitalidentity.rc.controller.api.model;

import dk.digitalidentity.rc.dao.model.ManagerSubstitute;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ManagerSubstituteDTO {
	private String uuid;
	private String name;
	private String userId;
	private String orgUnitUuid;
	private String orgUnitName;
	private String managerUuid;
	private String managerUserId;

	public ManagerSubstituteDTO(ManagerSubstitute ms) {
		this.uuid = ms.getSubstitute().getUuid();
		this.name = ms.getSubstitute().getName();
		this.userId = ms.getSubstitute().getUserId();
		this.orgUnitName = ms.getOrgUnit().getName();
		this.orgUnitUuid = ms.getOrgUnit().getUuid();
		this.managerUserId = ms.getManager().getUserId();
		this.managerUuid = ms.getManager().getUuid();
	}
}
