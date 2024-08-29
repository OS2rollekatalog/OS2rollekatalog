package dk.digitalidentity.rc.service.nemlogin.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class CreateGroupRequest {
	private String name;
	private String description;
	private String organizationGroupIdentifier;
	private Scope scope;
	private String mainGroupType = "Regular";
}
