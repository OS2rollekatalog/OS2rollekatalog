package dk.digitalidentity.rc.service.model;

import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.Title;
import lombok.Getter;

public class AssignedThroughInfo {
	public enum RoleType { USERROLE, ROLEGROUP };
	public enum EntityType { ORGUNIT_DIRECT, ORGUNIT_TITLE, ROLEGROUP_DIRECT }
	
	@Getter
	private String message;
	
	@Getter
	private RoleType roleType;
	
	@Getter
	private EntityType entityType;
	
	@Getter
	private String entityUuid;

	public AssignedThroughInfo(RoleGroup roleGroup) {
		this.roleType = RoleType.ROLEGROUP;
		this.entityType = EntityType.ROLEGROUP_DIRECT;
		this.message = "Rollebuket: " + roleGroup.getName();
		this.entityUuid = null;
	}
	
	public AssignedThroughInfo(OrgUnit orgUnit, RoleType roleType) {
		this.roleType = roleType;
		this.entityType = EntityType.ORGUNIT_DIRECT;
		this.message = "Enhed: " + orgUnit.getName();
		this.entityUuid = orgUnit.getUuid();
	}
		
	public AssignedThroughInfo(OrgUnit orgUnit, Title title, RoleType roleType) {
		this.roleType = roleType;
		this.entityType = EntityType.ORGUNIT_TITLE;
		this.message = "Enhed/Stilling: " + orgUnit.getName() + " (" + title.getName() + ")";
		this.entityUuid = orgUnit.getUuid();
	}
}
