package dk.digitalidentity.rc.service.nemlogin.model;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NemLoginAllRolesResponse {
	List<NemLoginRole> roles;
}
