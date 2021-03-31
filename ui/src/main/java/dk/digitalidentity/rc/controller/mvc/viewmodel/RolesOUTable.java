package dk.digitalidentity.rc.controller.mvc.viewmodel;

import dk.digitalidentity.rc.dao.model.OrgUnit;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RolesOUTable {
	private String uuid;
	private String name;
	private String parentBreadcrumbs;
	private boolean checked;
	private Assignment assignment;
	
	public RolesOUTable(OrgUnit ou, boolean checked, Assignment assignment) {
		this.uuid = ou.getUuid();
		this.name = ou.getName();
		
		StringBuilder parentBuilder = new StringBuilder();
		OrgUnit parent = ou.getParent();
		int counter = 0;
		while (parent != null && counter < 2) {
			parentBuilder.insert(0, " -> ");
			parentBuilder.insert(0, parent.getName());
	
			parent = parent.getParent();
			counter++;
		}
		
		this.parentBreadcrumbs = parentBuilder.toString();
		
		this.checked = checked;
		this.assignment = assignment;
	}
}
