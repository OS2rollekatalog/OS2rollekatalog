package dk.digitalidentity.rc.service.model;

import dk.digitalidentity.rc.dao.model.OrgUnit;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrgUnitWithNewAndOldParentDTO {
	private OrgUnit orgUnit;
	private OrgUnit newParent;
	private OrgUnit oldParent;
}
