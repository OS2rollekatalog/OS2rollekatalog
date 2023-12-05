package dk.digitalidentity.rc.service.dmp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class DMPCreateUser {
	private String email;
	private String firstName;
	private String lastName;
	private String externalUserId;
}
