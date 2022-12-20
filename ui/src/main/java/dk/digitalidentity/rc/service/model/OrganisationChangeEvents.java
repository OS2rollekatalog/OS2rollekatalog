package dk.digitalidentity.rc.service.model;

import java.util.HashSet;
import java.util.Set;

import dk.digitalidentity.rc.dao.model.OrgUnit;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrganisationChangeEvents {
	private Set<OrgUnit> newOrgUnits;
	private Set<OrgUnitWithNewAndOldParentDTO> orgUnitsWithNewParent;
	private Set<OrgUnitWithTitlesDTO> orgUnitsWithNewTitles;
	private Set<UserMovedPositions> usersMovedPostions;
	private Set<UserDeletedEvent> deletedUsers;
	
	public OrganisationChangeEvents() {
		newOrgUnits = new HashSet<>();
		orgUnitsWithNewParent = new HashSet<>();
		orgUnitsWithNewTitles = new HashSet<>();
		usersMovedPostions = new HashSet<>();
		deletedUsers = new HashSet<>();
	}
}
