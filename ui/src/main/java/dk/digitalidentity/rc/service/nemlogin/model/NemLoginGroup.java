package dk.digitalidentity.rc.service.nemlogin.model;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NemLoginGroup {
	private List<NemLoginRole> roles;
	private String uuid;
	private String name;
	private String description;
	private String organizationGroupIdentifier;
	private Scope scope;
}
