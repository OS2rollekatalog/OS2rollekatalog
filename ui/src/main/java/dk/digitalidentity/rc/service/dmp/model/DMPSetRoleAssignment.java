package dk.digitalidentity.rc.service.dmp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class DMPSetRoleAssignment {
	private String roleId;
	private String expirationTime;
}
