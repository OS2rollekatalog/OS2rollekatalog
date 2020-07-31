package dk.digitalidentity.rc.controller.api;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import dk.digitalidentity.rc.controller.api.dto.ItSystemDTO;
import dk.digitalidentity.rc.controller.api.dto.ItSystemWithSystemRolesDTO;
import dk.digitalidentity.rc.controller.api.dto.SystemRoleDTO;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.SystemRole;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.SystemRoleService;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.rc.service.model.UserWithRole;

@RestController
public class ItSystemApi {

	@Autowired
	private ItSystemService itSystemService;
	
	@Autowired
	private UserRoleService userRoleService;
	
	@Autowired
	private UserService userService;

	@Autowired
	private SystemRoleService systemRoleService;

	@GetMapping(value = "/api/itsystem/all")
	public ResponseEntity<List<ItSystemDTO>> getAllItSystems() {
		List<ItSystemDTO> itSystems = itSystemService.getAll().stream().map(its -> new ItSystemDTO(its.getId(), its.getName(), its.getIdentifier())).collect(Collectors.toList());

		return new ResponseEntity<>(itSystems, HttpStatus.OK);
	}

	@GetMapping(value = "/api/itsystem/manage")
	public ResponseEntity<List<ItSystemDTO>> manageItSystems() {
		List<ItSystemDTO> itSystems = itSystemService.getAll().stream()
				.filter(its -> its.isCanEditThroughApi() && (its.getSystemType() == ItSystemType.AD || its.getSystemType() == ItSystemType.SAML))
				.map(its -> new ItSystemDTO(its.getId(), its.getName(), its.getIdentifier()))
				.collect(Collectors.toList());

		return new ResponseEntity<>(itSystems, HttpStatus.OK);
	}

	@GetMapping(value = "/api/itsystem/manage/{id}")
	public ResponseEntity<ItSystemWithSystemRolesDTO> manageItSystem(@PathVariable("id") Long id) {
		ItSystemWithSystemRolesDTO result = new ItSystemWithSystemRolesDTO();

		ItSystem itSystem = itSystemService.getById(id);
		if (itSystem == null) {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}

		if (itSystem.isCanEditThroughApi() == false) {
			return new ResponseEntity<>(HttpStatus.FORBIDDEN);
		}

		if (itSystem.getSystemType() != ItSystemType.AD && itSystem.getSystemType() != ItSystemType.SAML) {
			return new ResponseEntity<>(HttpStatus.FORBIDDEN);
		}

		List<SystemRoleDTO> systemRoles = systemRoleService.getByItSystem(itSystem).stream()
				.map(sr -> new SystemRoleDTO(sr))
				.collect(Collectors.toList());

		result = new ItSystemWithSystemRolesDTO(itSystem.getId(), itSystem.getName(), itSystem.getIdentifier());
		result.setSystemRoles(systemRoles);

		return new ResponseEntity<>(result, HttpStatus.OK);
	}

	@PostMapping(value = "/api/itsystem/manage/{id}")
	public ResponseEntity<?> manageItSystem(@PathVariable("id") Long id, @RequestBody @Valid ItSystemWithSystemRolesDTO body) {
		ItSystem itSystem = itSystemService.getById(id);
		if (itSystem == null) {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}

		if (itSystem.isCanEditThroughApi() == false) {
			return new ResponseEntity<>(HttpStatus.FORBIDDEN);
		}

		if (itSystem.getSystemType() != ItSystemType.AD && itSystem.getSystemType() != ItSystemType.SAML) {
			return new ResponseEntity<>(HttpStatus.FORBIDDEN);
		}

		List<SystemRole> existingSystemRoles = systemRoleService.getByItSystem(itSystem);
		for (SystemRoleDTO systemRoleDTO : body.getSystemRoles()) {
			if (existingSystemRoles.stream().anyMatch(sr -> Objects.equals(sr.getIdentifier(), systemRoleDTO.getIdentifier()))) {
				SystemRole existingSystemRole = existingSystemRoles.stream()
						.filter(sr -> Objects.equals(sr.getIdentifier(), systemRoleDTO.getIdentifier()))
						.findAny().get();

				existingSystemRole.setDescription(systemRoleDTO.getDescription());
				existingSystemRole.setName(systemRoleDTO.getName());
				
				// update existing systemroles
				systemRoleService.save(existingSystemRole);
			}
			else {
				SystemRole newSystemRole = new SystemRole();
				newSystemRole.setIdentifier(systemRoleDTO.getIdentifier());
				newSystemRole.setName(systemRoleDTO.getName());
				newSystemRole.setDescription(systemRoleDTO.getDescription());
				newSystemRole.setItSystem(itSystem);

				// add new systemroles
				systemRoleService.save(newSystemRole);
			}
		}
		
		// delete removed systemroles
		for (SystemRole systemRole : existingSystemRoles) {
			if(body.getSystemRoles().stream().noneMatch(sr -> Objects.equals(sr.getIdentifier(), systemRole.getIdentifier()))){
				systemRoleService.delete(systemRole);
			}
		}

		// update it-system
		itSystem.setName(body.getName());
		itSystemService.save(itSystem);

		return new ResponseEntity<>(HttpStatus.OK);
	}

	@GetMapping(value = "/api/itsystem/{id}/users")
	public ResponseEntity<Set<String>> getUsersForItSystem(@PathVariable("id") String id) {
		Set<String> result =  new HashSet<>();

		ItSystem itSystem = itSystemService.getFirstByIdentifier(id);
		if (itSystem == null) {
			// we also allow looking up using UUID for KOMBIT based it-systems
			itSystem = itSystemService.getByUuid(id);

			if (itSystem == null) {
				return new ResponseEntity<>(result, HttpStatus.NOT_FOUND);
			}
		}

		List<UserRole> userRoles = userRoleService.getByItSystem(itSystem);
		for (UserRole userRole : userRoles) {
			Set<String> users = getUsersWithUserRole(userRole);

			result.addAll(users);
		}

		return new ResponseEntity<>(result, HttpStatus.OK);

	}
	
	private Set<String> getUsersWithUserRole(UserRole userRole) {
		Set<String> users = new HashSet<>();

		List<UserWithRole> usersWithRole = userService.getUsersWithUserRole(userRole, true);
		for (UserWithRole userWithRole : usersWithRole) {
			users.add(userWithRole.getUser().getUserId());
		}
		
		return users;
	}

}
