package dk.digitalidentity.rc.service.nemlogin.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AssignedRole {
	private Scope scope;
	
	@JsonProperty(value = "isInternal")
	private boolean internal;

	private String from;
	private String uuid;
	private String name;
	private String description;
	private String teaserText;
}
