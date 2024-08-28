package dk.digitalidentity.rc.service.nemlogin.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class NemLoginGroup {
	private List<NemLoginRole> roles;
	private String uuid;
	private String name;
	private String description;
	private String organizationGroupIdentifier;
	private Scope scope;
}
