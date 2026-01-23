package dk.digitalidentity.rc.rolerequest.model.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CombinedRoleDTO {
	private long id;
	private String type;
	private String itSystemName;
	private String name;
	private String description;
	private String approver;
	private boolean alreadyAssigned;
	private boolean hasConstraints;
	// This is simply a comma separated list that includes all the roles that are present within a single role group
	// this value is used for search functionality
	private String roleWithinRoleGroup;

	public CombinedRoleDTO(long id, String type, String itSystemName, String name, String description,
						   String approver, boolean alreadyAssigned, boolean hasConstraints, String roleWithinRoleGroup) {
		this.id = id;
		this.type = type;
		this.itSystemName = itSystemName;
		this.name = name;
		this.description = description;
		this.approver = approver;
		this.alreadyAssigned = alreadyAssigned;
		this.hasConstraints = hasConstraints;
		this.roleWithinRoleGroup = roleWithinRoleGroup;
	}

}
