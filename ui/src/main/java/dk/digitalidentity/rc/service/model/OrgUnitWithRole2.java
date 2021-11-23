package dk.digitalidentity.rc.service.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrgUnitWithRole2 {
	public String ouUuid;
	public String ouName;
	public RoleAssignedToOrgUnitDTO assignment;
}
