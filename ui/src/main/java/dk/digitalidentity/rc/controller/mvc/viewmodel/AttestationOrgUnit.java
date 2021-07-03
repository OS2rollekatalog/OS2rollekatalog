package dk.digitalidentity.rc.controller.mvc.viewmodel;

import java.util.Date;

import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.Position;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AttestationOrgUnit {
	private boolean canEdit;
	private boolean attestationPdfAVailable = false;
	private String uuid;
	private String managerName;
	private String managerPosition;
	private String substituteName;
	private String substitutePosition;
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
			
			if (ou.getManager().getManagerSubstitute() != null) {
				substituteName = ou.getManager().getManagerSubstitute().getName();
				if (ou.getManager().getManagerSubstitute().getPositions().size() > 0) {
					substitutePosition = ou.getManager().getManagerSubstitute().getPositions().get(0).getName() + " i " + ou.getManager().getManagerSubstitute().getPositions().get(0).getOrgUnit().getName();
				}
			}
		}
		
		// trick to avoid loading the PDF from DB
		if (ou.getLastAttested() != null) {
			attestationPdfAVailable = true;
		}
	}
}
