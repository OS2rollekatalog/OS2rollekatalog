package dk.digitalidentity.rc.service.nemlogin.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateGroupRequest {
	private String name;
	private String description;
	private String organizationGroupIdentifier;
	private Scope scope;
}
