package dk.digitalidentity.rc.controller.v2.api;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import dk.digitalidentity.rc.controller.v2.api.model.ManagedItSystemDTO;
import dk.digitalidentity.rc.controller.v2.api.model.SimpleUserRoleDTO;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.security.RequireApiRoleManagementRole;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.UserRoleService;

@RequireApiRoleManagementRole
@RestController
public class ItSystemApiV2 {

	@Autowired
	private ItSystemService itSystemService;

	@Autowired
	private UserRoleService userRoleService;

	@GetMapping("/api/itSystem/managed")
	public ResponseEntity<List<ManagedItSystemDTO>> getAllItSystems() {
		List<ManagedItSystemDTO> results = new ArrayList<ManagedItSystemDTO>();

		List<ItSystem> itSystems = itSystemService.getAll()
				.stream()
				.filter(its -> its.isApiManagedRoleAssignments())
				.collect(Collectors.toList());

		for (ItSystem itSystem : itSystems) {
			List<SimpleUserRoleDTO> userRoles = userRoleService.getByItSystem(itSystem)
					.stream()
					.map(ur -> new SimpleUserRoleDTO(ur))
					.collect(Collectors.toList());

			ManagedItSystemDTO dto = new ManagedItSystemDTO();
			dto.setItSystemId(itSystem.getId());
			dto.setItSystemName(itSystem.getName());
			dto.setRoles(userRoles);

			results.add(dto);
		}

		return new ResponseEntity<>(results, HttpStatus.OK);
	}
}
