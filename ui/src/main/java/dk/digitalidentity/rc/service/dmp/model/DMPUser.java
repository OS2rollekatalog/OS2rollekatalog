package dk.digitalidentity.rc.service.dmp.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class DMPUser {
	private String id;
	private String organizationId;
	private String email;
	private String firstname;
	private String lastname;
	private String description;
	
	// er med i output, men er altid tom... skal dog ikke bruge den til noget umiddelbart
	// private String userRoleAssignment;

	private List<String> externalUserIds;
}
