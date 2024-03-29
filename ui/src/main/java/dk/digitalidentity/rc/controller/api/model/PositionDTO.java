package dk.digitalidentity.rc.controller.api.model;

import dk.digitalidentity.rc.dao.model.Position;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PositionDTO {
	private String name;
	private String orgUnitUuid;
	private String titleUuid;
	// Boolean to support null-check for clients that do not send this property
	private Boolean doNotInherit;

	public PositionDTO(Position position) {
		this.name = position.getName();
		this.orgUnitUuid = position.getOrgUnit() != null ? position.getOrgUnit().getUuid() : null;
		this.titleUuid = position.getTitle() != null ? position.getTitle().getUuid() : null;
		this.doNotInherit = position.isDoNotInherit();
	}

	public PositionDTO(String name, String orgUnitUuid, String titleUuid, boolean doNotInherit) {
		this.name = name;
		this.orgUnitUuid = orgUnitUuid;
		this.titleUuid = titleUuid;
		this.doNotInherit = doNotInherit;
	}
}
