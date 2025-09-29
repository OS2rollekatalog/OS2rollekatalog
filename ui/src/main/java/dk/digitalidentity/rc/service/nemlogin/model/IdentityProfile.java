package dk.digitalidentity.rc.service.nemlogin.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class IdentityProfile {
	private HashSet<String> roles;
}
