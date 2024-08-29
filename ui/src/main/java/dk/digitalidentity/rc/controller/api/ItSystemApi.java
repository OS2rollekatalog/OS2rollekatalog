package dk.digitalidentity.rc.controller.api;

import dk.digitalidentity.rc.controller.api.dto.ItSystemDTO;
import dk.digitalidentity.rc.controller.api.dto.ItSystemWithSystemRolesDTO;
import dk.digitalidentity.rc.controller.api.dto.SystemRoleDTO;
import dk.digitalidentity.rc.controller.api.dto.UserRoleDTO;
import dk.digitalidentity.rc.dao.model.Domain;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.SystemRole;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignment;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import dk.digitalidentity.rc.dao.model.enums.RoleType;
import dk.digitalidentity.rc.security.RequireApiItSystemRole;
import dk.digitalidentity.rc.service.DomainService;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.SystemRoleService;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.rc.service.model.UserWithRole;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@RequireApiItSystemRole
@Slf4j
@RestController
@SecurityRequirement(name = "ApiKey")
public class ItSystemApi {

	@Autowired
	private ItSystemService itSystemService;
	
	@Autowired
	private UserRoleService userRoleService;
	
	@Autowired
	private UserService userService;

	@Autowired
	private SystemRoleService systemRoleService;

	@Autowired
	private DomainService domainService;
	
	@GetMapping(value = "/api/itsystem/all")
	public ResponseEntity<List<ItSystemDTO>> getAllItSystems() {
		List<ItSystemDTO> itSystems = itSystemService.getAll().stream()
				.map(its -> new ItSystemDTO(its.getId(), its.getName(), its.getIdentifier()))
				.collect(Collectors.toList());

		return new ResponseEntity<>(itSystems, HttpStatus.OK);
	}

	@GetMapping(value = "/api/itsystem/manage")
	public ResponseEntity<List<ItSystemDTO>> manageItSystems() {
		List<ItSystemDTO> itSystems = itSystemService.getAll().stream()
				.filter(its -> its.isCanEditThroughApi() && (its.getSystemType() == ItSystemType.AD || its.getSystemType() == ItSystemType.SAML || its.getSystemType() == ItSystemType.MANUAL))
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

		if (itSystem.getSystemType() != ItSystemType.AD && itSystem.getSystemType() != ItSystemType.SAML && itSystem.getSystemType() != ItSystemType.MANUAL) {
			return new ResponseEntity<>(HttpStatus.FORBIDDEN);
		}

		List<SystemRoleDTO> systemRoles = systemRoleService.getByItSystem(itSystem).stream()
				.map(sr -> new SystemRoleDTO(sr))
				.collect(Collectors.toList());

		List<UserRoleDTO> userRoles = userRoleService.getByItSystem(itSystem).parallelStream()
				.map(ur -> new UserRoleDTO(ur))
				.collect(Collectors.toList());

		result = new ItSystemWithSystemRolesDTO(itSystem.getId(), itSystem.getName(), itSystem.getIdentifier());
		result.setSystemRoles(systemRoles);
		result.setUserRoles(userRoles);

		return new ResponseEntity<>(result, HttpStatus.OK);
	}

	@PostMapping(value = "/api/itsystem/manage/{id}")
	public ResponseEntity<?> manageItSystem(@PathVariable("id") Long id, @RequestParam(name = "updateUserAssignments", required = false, defaultValue = "false") boolean updateUserAssignments, @RequestParam(name = "domain", required = false) String domain, @RequestBody @Valid ItSystemWithSystemRolesDTO body) {
		log.info("manage API on " + id + " called");
		
		ItSystem itSystem = itSystemService.getById(id);
		if (itSystem == null) {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}

		if (itSystem.isCanEditThroughApi() == false) {
			return new ResponseEntity<>(HttpStatus.FORBIDDEN);
		}

		if (itSystem.getSystemType() != ItSystemType.AD && itSystem.getSystemType() != ItSystemType.SAML && itSystem.getSystemType() != ItSystemType.MANUAL) {
			return new ResponseEntity<>(HttpStatus.FORBIDDEN);
		}

		// while normally not something we would set on non-AD it-systems, we still need to known which domain
		// the users are coming from, so we have to do this check (we only get AD usernames for the users, not UUIDs)
		Domain foundDomain = domainService.getDomainOrPrimary(domain);
		if (foundDomain == null) {
			log.warn("Unable to find a domain for it-system");
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
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
				newSystemRole.setRoleType(RoleType.BOTH);

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
		itSystem.setReadonly(body.isReadonly());
		itSystemService.save(itSystem);

		if (body.isConvertRolesEnabled()) {
			boolean containsUsers = false;

			if (body.getSystemRoles() != null) {
				for (SystemRoleDTO dto : body.getSystemRoles()) {
					if (dto.getUsers() != null && dto.getUsers().size() > 0) {
						containsUsers = true;
						break;
					}				
				}
			}

			Map<String, User> users = new HashMap<>();
			if (containsUsers) {
				for (User user : userService.getByDomain(foundDomain)) {
					users.put(user.getUserId().toLowerCase(), user);
				}
			}
			
			// ensure 1:1 user roles with same name as system-role and delete any user-role without system-roles
			// we keep track of corresponding 1:1 roles by assigning the same identifier on both entities
			List<SystemRole> systemRoles = systemRoleService.getByItSystem(itSystem);
			List<UserRole> userRoles = userRoleService.getByItSystem(itSystem);

			// Update user role to match name of system role
			List<UserRole> toBeUpdated = userRoles.stream()
					.filter(ur -> systemRoles.stream().anyMatch(sr -> Objects.equals(sr.getIdentifier(), ur.getIdentifier())))
					.collect(Collectors.toList());

			for (UserRole userRole : toBeUpdated) {
				String identifier = userRole.getIdentifier();
				Optional<SystemRole> systemRole = systemRoles.stream()
						.filter(sr -> Objects.equals(sr.getIdentifier(), identifier))
						.findFirst();

				if (!systemRole.isPresent()) {
					continue;
				}

				SystemRole sRole = systemRole.get();

				if (!Objects.equals(userRole.getName(), sRole.getName()) ||
					!Objects.equals(userRole.getDescription(), sRole.getDescription())) {
					userRole.setName(sRole.getName());
					userRole.setDescription(sRole.getDescription());
					userRole = userRoleService.save(userRole);
				}

				if (itSystem.isReadonly() || updateUserAssignments) {
					Optional<SystemRoleDTO> systemRoleDTO = body.getSystemRoles().stream()
							.filter(srDTO -> Objects.equals(srDTO.getIdentifier(), systemRole.get().getIdentifier()))
							.findFirst();
					
					if (!systemRoleDTO.isPresent()) {
						continue;
					}

					updateUserAssignments(systemRoleDTO.get(), userRole, users);
				}
			}
			
			// create 1:1 user role
			var toBeCreated = systemRoles.stream().filter(sr -> userRoles.stream().noneMatch(ur -> Objects.equals(ur.getIdentifier(), sr.getIdentifier()))).collect(Collectors.toList());
			for (SystemRole systemRole : toBeCreated) {
				UserRole userRole = new UserRole();
				userRole.setItSystem(itSystem);
				userRole.setIdentifier(systemRole.getIdentifier());
				userRole.setName(systemRole.getName());
				userRole.setDescription(systemRole.getDescription());

				SystemRoleAssignment systemRoleAssignment = new SystemRoleAssignment();
				systemRoleAssignment.setAssignedByName("Systembruger");
				systemRoleAssignment.setAssignedByUserId("Systembruger");
				systemRoleAssignment.setAssignedTimestamp(new Date());
				systemRoleAssignment.setSystemRole(systemRole);
				systemRoleAssignment.setUserRole(userRole);
				systemRoleAssignment.setConstraintValues(new ArrayList<>());
				
				userRole.setSystemRoleAssignments(Arrays.asList(systemRoleAssignment));

				userRole = userRoleService.save(userRole);

				// always relevant for create scenarios
				if (containsUsers) {
					Optional<SystemRoleDTO> systemRoleDTO = body.getSystemRoles().stream()
							.filter(srDTO -> Objects.equals(srDTO.getIdentifier(), systemRole.getIdentifier()))
							.findFirst();
					
					if (!systemRoleDTO.isPresent()) {
						continue;
					}

					updateUserAssignments(systemRoleDTO.get(), userRole, users);
				}
			}

			// delete user roles that has no system role assignments
			var toBeDeleted = userRoles.stream().filter(ur -> ur.getSystemRoleAssignments().size() == 0).collect(Collectors.toList());
			for (var userRole : toBeDeleted) {
				try {
					// TODO: if the userRole is included in a RoleGroup, this will fail - need a "on delete cascade" rule to the reference *sigh*
					userRoleService.delete(userRole);
				}
				catch (Exception ex) {
					log.error("Failed to delete userRole: " + userRole.getId(), ex);
				}
			}
		}

		return new ResponseEntity<>(HttpStatus.OK);
	}

	private void updateUserAssignments(SystemRoleDTO systemRoleDTO, UserRole userRole, Map<String, User> users) {
		List<String> assignedUsers = systemRoleDTO.getUsers();
		if (assignedUsers == null || assignedUsers.size() == 0) {
			assignedUsers = new ArrayList<String>();
		}

		// assign
		for (String userId : assignedUsers) {
			User user = users.get(userId);
			if (user == null) {
				log.warn("ItSystemApi: Unable to find user with userID: " + userId + " while updating UserRoles.");
				continue;
			}

			if (!user.getUserRoleAssignments().stream().anyMatch(ura -> ura.getUserRole().getId() == userRole.getId())) {
				userService.addUserRole(user, userRole, null, null);
			}
		}

		// remove
		List<UserWithRole> usersWithRole = userService.getUsersWithUserRole(userRole, false);
		for (UserWithRole userWithRole : usersWithRole) {
			String userId = userWithRole.getUser().getUserId();

			if (!assignedUsers.stream().anyMatch(u -> u.equalsIgnoreCase(userId))) {
				userService.removeUserRole(userWithRole.getUser(), userRole);
			}
		}
	}

	@GetMapping(value = "/api/itsystem/{id}/users")
	public ResponseEntity<Set<String>> getUsersForItSystem(@PathVariable("id") String id, @RequestParam(name = "domain", required = false) String domain) {
		Domain foundDomain = domainService.getDomainOrPrimary(domain);
		if (foundDomain == null) {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}
		Set<String> result =  new HashSet<>();

		ItSystem itSystem = null;

		try {
			itSystem = itSystemService.getById(Long.parseLong(id));
		}
		catch (Exception ex) {
			; // ignore
		}

		if (itSystem == null) {
			itSystem = itSystemService.getFirstByIdentifier(id);

			if (itSystem == null) {
				// we also allow looking up using UUID for KOMBIT based it-systems
				itSystem = itSystemService.getByUuid(id);
	
				if (itSystem == null) {
					return new ResponseEntity<>(result, HttpStatus.NOT_FOUND);
				}
			}
		}

		List<UserRole> userRoles = userRoleService.getByItSystem(itSystem);
		for (UserRole userRole : userRoles) {
			Set<String> users = getUsersWithUserRole(userRole, foundDomain);

			result.addAll(users);
		}

		return new ResponseEntity<>(result, HttpStatus.OK);

	}
	
	private Set<String> getUsersWithUserRole(UserRole userRole, Domain domain) {
		Set<String> users = new HashSet<>();

		List<UserWithRole> usersWithRole = userService.getUsersWithUserRole(userRole, true);
		for (UserWithRole userWithRole : usersWithRole) {
			if (userWithRole.getUser().isDeleted() || userWithRole.getUser().isDisabled() || !Objects.equals(domain.getName(), userWithRole.getUser().getDomain().getName())) {
				continue;
			}

			users.add(userWithRole.getUser().getUserId());
		}
		
		return users;
	}
}
