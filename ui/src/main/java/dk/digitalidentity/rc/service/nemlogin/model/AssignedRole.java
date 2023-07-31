package dk.digitalidentity.rc.service.nemlogin.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AssignedRole {
	private Scope scope;
	private boolean isInternal;
	private String uuid;
	private String name;
	private String description;
	private String teaserText;
}
