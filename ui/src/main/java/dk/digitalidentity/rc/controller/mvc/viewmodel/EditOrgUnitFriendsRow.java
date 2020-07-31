package dk.digitalidentity.rc.controller.mvc.viewmodel;

import dk.digitalidentity.rc.dao.model.OrgUnit;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EditOrgUnitFriendsRow {
	private OrgUnit candidate;
	private boolean isFriend;
}
