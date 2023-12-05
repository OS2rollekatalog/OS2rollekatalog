package dk.digitalidentity.rc.service.dmp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class DMPRole {
	private String id;
	private String attribute;
	private String name;
	private String description;
	private String applicationId;
}
