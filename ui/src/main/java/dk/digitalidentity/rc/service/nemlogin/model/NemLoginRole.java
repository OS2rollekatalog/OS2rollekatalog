package dk.digitalidentity.rc.service.nemlogin.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class NemLoginRole {
	private boolean isBasicPackage;
	private String uuid;
	private String name;
	private String description;
}
