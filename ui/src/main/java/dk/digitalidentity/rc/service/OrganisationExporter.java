package dk.digitalidentity.rc.service;

import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dk.digitalidentity.rc.controller.api.model.OrgUnitDTO;
import dk.digitalidentity.rc.controller.api.model.OrganisationDTO;
import dk.digitalidentity.rc.controller.api.model.UserDTO;

@Service
public class OrganisationExporter {

	@Autowired
	private UserService userService;

	@Autowired
	private OrgUnitService orgUnitService;
	
	public OrganisationDTO getOrganisationDTO() {
		OrganisationDTO organisationDTO = new OrganisationDTO();

		// OrgUnits
		List<OrgUnitDTO> ouDTOs = orgUnitService.getAll().stream().map(OrgUnitDTO::new).collect(Collectors.toList());
		organisationDTO.setOrgUnits(ouDTOs);

		// Users
		List<UserDTO> userDTOs = userService.getAll().stream().filter(user -> !CollectionUtils.isEmpty(user.getPositions())).map(UserDTO::new).collect(Collectors.toList());
		organisationDTO.setUsers(userDTOs);

		return organisationDTO;
	}
}
