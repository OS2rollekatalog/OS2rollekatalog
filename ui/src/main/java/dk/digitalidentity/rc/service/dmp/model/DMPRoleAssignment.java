package dk.digitalidentity.rc.service.dmp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class DMPRoleAssignment {
	private DMPRole role;
	private String expirationTime;
}
