package dk.digitalidentity.rc.service.model;

import java.util.HashSet;
import java.util.Set;

import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.Title;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrgUnitWithTitlesDTO {
	private OrgUnit orgUnit;
	private Set<Title> newTitles;
	
	public OrgUnitWithTitlesDTO() {
		newTitles = new HashSet<>();
		orgUnit = null;
	}
}
