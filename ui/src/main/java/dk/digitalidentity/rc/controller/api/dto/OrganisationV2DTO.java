package dk.digitalidentity.rc.controller.api.dto;

import java.util.List;

import dk.digitalidentity.rc.controller.api.model.FullOrgUnitAM;
import dk.digitalidentity.rc.controller.api.model.FullUserAM;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrganisationV2DTO {
	private List<FullUserAM> users;
	private List<FullOrgUnitAM> orgUnits;
}