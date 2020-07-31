package dk.digitalidentity.rc.controller.api.model;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrganisationDTO {
	private List<UserDTO> users;
	private List<OrgUnitDTO> orgUnits;
}