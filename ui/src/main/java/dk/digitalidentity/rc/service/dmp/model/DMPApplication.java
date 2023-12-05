package dk.digitalidentity.rc.service.dmp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class DMPApplication {
	private String id;
	private String name;
	private String description;
	private String ownerOrganizationId;
	private String coverImageUrl;
	private String logoUrl;
}
