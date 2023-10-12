package dk.digitalidentity.rc.controller.mvc.viewmodel;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import dk.digitalidentity.rc.dao.model.ManagerSubstitute;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.Position;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AttestationOrgUnit {
	private boolean canEdit;
	private boolean attestationPdfAvailable = false;
	private String uuid;
	private String managerName;
	private String managerPosition;
	private List<String> substitutes;
	private String name;
	private String lastAttestedBy;
	private Date lastAttested;
	private Date nextAttestation;

	public AttestationOrgUnit(OrgUnit ou, boolean canEdit) {
		this.canEdit = canEdit;
		this.name = ou.getName();
		this.uuid = ou.getUuid();
		this.lastAttested = ou.getLastAttested();
		this.nextAttestation = ou.getNextAttestation();
		this.lastAttestedBy = ou.getLastAttestedBy();
		
		if (ou.getManager() != null) {
			managerName = ou.getManager().getName();

			// prefer a position within the OU
			for (Position p : ou.getManager().getPositions()) {
				if (p.getOrgUnit().getUuid().equals(ou.getUuid())) {
					managerPosition = p.getName() + " i " + ou.getName();
					break;
				}
			}
			
			// if the manager is not employed in the OU where they are manager, just pick the first position
			if (managerPosition == null) {
				if (ou.getManager().getPositions().size() > 0) {
					managerPosition = ou.getManager().getPositions().get(0).getName() + " i " + ou.getManager().getPositions().get(0).getOrgUnit().getName();
				}
			}

			substitutes = new ArrayList<>();
			for (ManagerSubstitute ms : ou.getManager().getManagerSubstitutes()) {
				if (Objects.equals(ms.getOrgUnit().getUuid(), ou.getUuid())) {
					substitutes.add(ms.getSubstitute().getName());
				}
			}
		}
		
		// trick to avoid loading the PDF from DB
		if (ou.getLastAttested() != null) {
			attestationPdfAvailable = true;
		}
	}
}
