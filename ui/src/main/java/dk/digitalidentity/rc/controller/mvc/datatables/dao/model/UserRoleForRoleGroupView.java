package dk.digitalidentity.rc.controller.mvc.datatables.dao.model;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "user_roles") // Point to base table, not view
@NoArgsConstructor
public class UserRoleForRoleGroupView {
	@EmbeddedId
	private UserRoleForRoleGroupId compositeKey;
	private String name;
	private String description;
	private String itSystemName;
	private Boolean selected;
	private Boolean readOnly;
	
	
	// Constructor for native query results
	public UserRoleForRoleGroupView(Long id, String name, String description, 
			String itSystemName, Long rolegroupId, Boolean selected, Boolean readOnly) {
		this.compositeKey = new UserRoleForRoleGroupId(id, rolegroupId);
		this.name = name;
		this.description = description;
		this.itSystemName = itSystemName;
		this.selected = selected;
		this.readOnly = readOnly;
	}
}