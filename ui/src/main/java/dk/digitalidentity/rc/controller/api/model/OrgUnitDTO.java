package dk.digitalidentity.rc.controller.api.model;

import java.util.List;

import javax.persistence.Transient;

import dk.digitalidentity.rc.dao.model.enums.OrgUnitLevel;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrgUnitDTO {
	private String uuid;
	private String name;
	private String parentOrgUnitUuid;
	private boolean inheritKle;
	private OrgUnitLevel level;
	private List<String> klePerforming;
	private List<String> kleInterest;
	private List<Long> itSystemIdentifiers;
	private ManagerDTO manager;
	
	@Transient
	private OrgUnitDTO _parentRef;
}
