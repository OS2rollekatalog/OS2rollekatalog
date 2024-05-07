package dk.digitalidentity.rc.controller.api.model;

import dk.digitalidentity.rc.dao.model.KLEMapping;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.enums.KleType;
import dk.digitalidentity.rc.dao.model.enums.OrgUnitLevel;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.stream.Collectors;

@Getter
@Setter
@NoArgsConstructor
public class OrgUnitDTO {
	private String uuid;
	private String name;
	private String parentOrgUnitUuid;
	private boolean inheritKle;
	private OrgUnitLevel level;
	private List<String> klePerforming;
	private List<String> kleInterest;
	private ManagerDTO manager;
	private List<String> titleIdentifiers;
	
	@Transient
	private OrgUnitDTO _parentRef;

	public OrgUnitDTO(OrgUnit orgUnit) {
		this.uuid = orgUnit.getUuid();
		this.name = orgUnit.getName();
		this.parentOrgUnitUuid = orgUnit.getParent() != null ? orgUnit.getParent().getUuid() : null;
		this.inheritKle = orgUnit.isInheritKle();
		this.level = orgUnit.getLevel();

		if (orgUnit.getManager() != null) {
			this.manager = new ManagerDTO(orgUnit.getManager());
		}

		if (orgUnit.getKles() != null) {
			this.klePerforming = orgUnit.getKles()
					.stream()
					.filter(kleMapping -> KleType.PERFORMING.equals(kleMapping.getAssignmentType()))
					.map(KLEMapping::getCode)
					.collect(Collectors.toList());


			this.kleInterest = orgUnit.getKles()
					.stream()
					.filter(kleMapping -> KleType.INTEREST.equals(kleMapping.getAssignmentType()))
					.map(KLEMapping::getCode)
					.collect(Collectors.toList());
		}

		if (orgUnit.getTitles() != null) {
			this.titleIdentifiers = orgUnit.getTitles().stream().map(t -> t.getUuid()).collect(Collectors.toList());
		}
	}

	public OrgUnitDTO(String uuid, String name, String parentOrgUnitUuid, boolean inheritKle, OrgUnitLevel level, List<String> klePerforming, List<String> kleInterest, ManagerDTO manager, List<String> titleIdentifiers) {
		this.uuid = uuid;
		this.name = name;
		this.parentOrgUnitUuid = parentOrgUnitUuid;
		this.inheritKle = inheritKle;
		this.level = level;
		this.klePerforming = klePerforming;
		this.kleInterest = kleInterest;
		this.manager = manager;
		this.titleIdentifiers = titleIdentifiers;
	}
}
