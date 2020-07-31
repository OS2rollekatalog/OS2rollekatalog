package dk.digitalidentity.rc.service.model;

import java.util.HashSet;
import java.util.Set;

import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.Position;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrganisationChangeEvents {
	private Set<OrgUnit> ousWithNewParent;
	private Set<OrgUnit> ousWithNewManager;
	private Set<Position> usersWithNewPosition;
	
	public OrganisationChangeEvents() {
		ousWithNewParent = new HashSet<>();
		ousWithNewManager = new HashSet<>();
		usersWithNewPosition = new HashSet<>();
	}
}
