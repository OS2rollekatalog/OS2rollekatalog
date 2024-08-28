package dk.digitalidentity.rc.service.nemlogin.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class NemLoginAllRolesResponse {
	List<NemLoginRole> roles;
}
