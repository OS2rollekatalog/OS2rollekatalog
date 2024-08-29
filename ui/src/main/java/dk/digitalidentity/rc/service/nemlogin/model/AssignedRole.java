package dk.digitalidentity.rc.service.nemlogin.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class AssignedRole {
	private Scope scope;
	
	@JsonProperty(value = "isInternal")
	private boolean internal;

	private String from;
	private String to;
	private String uuid;
	private String name;
	private String description;
	private String teaserText;
}
