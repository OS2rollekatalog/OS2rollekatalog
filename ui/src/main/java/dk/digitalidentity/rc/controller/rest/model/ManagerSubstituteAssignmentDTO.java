package dk.digitalidentity.rc.controller.rest.model;

import java.util.Date;

import dk.digitalidentity.rc.controller.api.model.ManagerSubstituteDTO;
import dk.digitalidentity.rc.dao.model.ManagerSubstitute;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ManagerSubstituteAssignmentDTO {
	private long id;
	private UserDTO manager;
	private ManagerSubstituteDTO substitute;
	private OrgUnitDTO orgUnit;
	private String assignedBy;
	private Date assignedTts;
	
	public ManagerSubstituteAssignmentDTO(ManagerSubstitute ms) {
		this.id = ms.getId();
		this.manager = new UserDTO(ms.getManager().getUuid(), ms.getManager().getName());
		this.substitute = new ManagerSubstituteDTO(ms);
		this.orgUnit = new OrgUnitDTO(ms.getOrgUnit().getUuid(), ms.getOrgUnit().getName());
		this.assignedBy = ms.getAssignedBy();
		this.assignedTts = ms.getAssignedTts();
	}
}
