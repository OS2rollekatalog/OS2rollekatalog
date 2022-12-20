package dk.digitalidentity.rc.controller.api.model;

import java.util.List;
import java.util.stream.Collectors;

import javax.persistence.Transient;

import dk.digitalidentity.rc.dao.model.KLEMapping;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.enums.KleType;
import dk.digitalidentity.rc.dao.model.enums.OrgUnitLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
}
