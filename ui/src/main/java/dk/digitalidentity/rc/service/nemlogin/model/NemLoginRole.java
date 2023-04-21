package dk.digitalidentity.rc.service.nemlogin.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NemLoginRole {
	private boolean isBasicPackage;
	private String uuid;
	private String name;
	private String description;
}
