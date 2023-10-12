package dk.digitalidentity.rc.controller.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dk.digitalidentity.rc.controller.api.dto.UserResponseDTO;
import dk.digitalidentity.rc.controller.api.dto.UserResponseWithOIOBPPDTO;
import dk.digitalidentity.rc.controller.api.dto.UserResponseWithRolesDTO;
import dk.digitalidentity.rc.dao.model.Domain;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.SystemRole;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignment;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.enums.EventType;
import dk.digitalidentity.rc.exceptions.UserNotFoundException;
import dk.digitalidentity.rc.log.AuditLogger;
import dk.digitalidentity.rc.security.RequireApiReadAccessRole;
import dk.digitalidentity.rc.service.DomainService;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.SystemRoleService;
import dk.digitalidentity.rc.service.UserService;
import lombok.extern.slf4j.Slf4j;

@RequireApiReadAccessRole
@Slf4j
@RestController
public class UserApi {

	@Autowired
	private UserService userService;
	
	@Autowired
	private AuditLogger auditLogger;
	
	@Autowired
	private ItSystemService itSystemService;
	
	@Autowired
	private SystemRoleService systemRoleService;

	@Autowired
	private DomainService domainService;

	@RequestMapping(value = "/api/user/{userid}/roles", method = RequestMethod.GET)
	public ResponseEntity<UserResponseWithOIOBPPDTO> getUserRoles(@PathVariable("userid") String userId, @RequestParam(name = "system", required = false) String itSystemIdentifier, @RequestParam(name = "domain", required = false) String domain) throws Exception {
		UserResponseWithOIOBPPDTO response = new UserResponseWithOIOBPPDTO();

		Domain foundDomain = domainService.getDomainOrPrimary(domain);
		if (foundDomain == null) {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}

		User user = getUser(userId, foundDomain);
		if (user == null) {
			log.warn("could not find user: " + userId);
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}

		List<ItSystem> itSystems = null;
		if (StringUtils.hasLength(itSystemIdentifier)) {
			itSystems = getItSystems(itSystemIdentifier);
			if (itSystems.size() == 0) {
				log.warn("could not find itSystem: " + itSystemIdentifier);
				return new ResponseEntity<>(HttpStatus.NOT_FOUND);
			}
		}

		try {
			Map<String, String> roleMap = new HashMap<>();
			String oioBpp = userService.generateOIOBPP(user, itSystems, roleMap);
			
			response.setOioBPP(oioBpp);
			response.setRoleMap(roleMap);
			response.setNameID(userService.getUserNameId(userId, foundDomain));

			// TODO: lav ikke opslag igen - brug listen af it-systemer og log den første
			auditLogger.log(user, EventType.LOGIN_EXTERNAL, itSystemService.getFirstByIdentifier(itSystemIdentifier));
		}
		catch (UserNotFoundException ex) {
			log.warn("could not find roles for " + userId + " for itSystem: " + itSystemIdentifier + ". Exception message: " + ex.getMessage());
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}

		return new ResponseEntity<>(response, HttpStatus.OK);
	}
	
	@RequestMapping(value = "/api/user/{userid}/rolesAsList", method = RequestMethod.GET)
	public ResponseEntity<UserResponseWithRolesDTO> getUserRolesAsList(@PathVariable("userid") String userId, @RequestParam(name = "system") String itSystemIdentifier, @RequestParam(name = "domain", required = false) String domain) throws Exception {
		UserResponseWithRolesDTO response = new UserResponseWithRolesDTO();

		Domain foundDomain = domainService.getDomainOrPrimary(domain);
		if (foundDomain == null) {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}

		User user = getUser(userId, foundDomain);
		if (user == null) {
			log.warn("could not find user: " + userId);
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}

		List<ItSystem> itSystems = null;
		if (StringUtils.hasLength(itSystemIdentifier)) {
			itSystems = getItSystems(itSystemIdentifier);
			if (itSystems.size() == 0) {
				log.warn("could not find itSystem: " + itSystemIdentifier);
				return new ResponseEntity<>(HttpStatus.NOT_FOUND);
			}
		}
		else {
			itSystems = itSystemService.getAll();
		}

		if (itSystems != null) {
			itSystems = itSystems.stream().filter(its -> its.isAccessBlocked() == false).collect(Collectors.toList());
		}

		try {
			List<UserRole> roles = userService.getAllUserRoles(user, itSystems);

			List<String> userRoleList = new ArrayList<>();
			for (UserRole role : roles) {
				userRoleList.add(role.getIdentifier());
			}

			// use a set to remove duplicates
			Set<String> systemRoleSet = new HashSet<>();
			Set<String> dataRoleSet = new HashSet<>();
			Set<String> functionRoleSet = new HashSet<>();
			Map<String, String> roleMap = new HashMap<>();
			
			Map<String, SystemRole> allAssignedSystemRoles = new HashMap<>();
			for (UserRole role : roles) {
				// might be counterintuitive, but while the output focuses on systemroles, we
				// are sending a map of userRoles, for logging purposes, so it matches the OIO-BPP output (again, logging consistency)
				roleMap.put(role.getIdentifier(), role.getName() + " (" + role.getItSystem().getName() + ")");
				
				for (SystemRoleAssignment systemRoleAssignment : role.getSystemRoleAssignments()) {
					allAssignedSystemRoles.put(systemRoleAssignment.getSystemRole().getIdentifier(), systemRoleAssignment.getSystemRole());
				}
			}

			for (SystemRole systemRole : allAssignedSystemRoles.values()) {
				boolean differentWeightedItSystem = systemRoleService.belongsToItSystemWithDifferentWeight(systemRole);
				if (differentWeightedItSystem) {
					boolean skip = false;
					
					for (SystemRole sr : allAssignedSystemRoles.values()) {
						if (sr.getItSystem().getId() != systemRole.getItSystem().getId()) {
							continue;
						}
						if (sr.getWeight() > systemRole.getWeight()) {
							skip = true;
							break;
						}
					}
					
					if (!skip) {
						systemRoleSet.add(systemRole.getIdentifier());
					}
				} else {
					systemRoleSet.add(systemRole.getIdentifier());
				}

				switch (systemRole.getRoleType()) {
					case DATA_ROLE:
						dataRoleSet.add(systemRole.getIdentifier());
						break;
					case FUNCTION_ROLE:
						functionRoleSet.add(systemRole.getIdentifier());
						break;
					default:
						break;
				}
			}
			List<String> systemRoleList = new ArrayList<>(systemRoleSet);
			List<String> dataRoleList = new ArrayList<>(dataRoleSet);
			List<String> functionRoleList = new ArrayList<>(functionRoleSet);

			response.setUserRoles(userRoleList);
			response.setSystemRoles(systemRoleList);
			response.setDataRoles(dataRoleList);
			response.setFunctionRoles(functionRoleList);
			response.setRoleMap(roleMap);

			response.setNameID(userService.getUserNameId(userId, foundDomain));

			auditLogger.log(userService.getByUserId(userId, foundDomain), EventType.LOGIN_EXTERNAL, itSystemService.getFirstByIdentifier(itSystemIdentifier));
		}
		catch (UserNotFoundException ex) {
			log.warn("could not find roles for " + userId + " for itSystem: " + itSystemIdentifier + ". Exception message: " + ex.getMessage());
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}

		return new ResponseEntity<>(response, HttpStatus.OK);
	}

	@RequestMapping(value = "/api/user/{userid}/nameid", method = RequestMethod.GET)
	public ResponseEntity<UserResponseDTO> getUserNameId(@PathVariable("userid") String userId, @RequestParam(name = "domain", required = false) String domain) throws Exception {
		UserResponseDTO response = new UserResponseDTO();

		Domain foundDomain = domainService.getDomainOrPrimary(domain);
		if (foundDomain == null) {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}

		try {
			response.setNameID(userService.getUserNameId(userId, foundDomain));
		}
		catch (UserNotFoundException ex) {
			log.warn("could not find nameId for " + userId + ". Exception message: " + ex.getMessage());
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}

		return new ResponseEntity<>(response, HttpStatus.OK);
	}
	
	@RequestMapping(value = "/api/user/{userid}/hasUserRole/{roleId}", method = RequestMethod.GET)
	public ResponseEntity<String> userHasUserRole(@PathVariable("userid") String userId, @PathVariable("roleId") long roleId, @RequestParam(name = "system", required = false) String itSystemIdentifier, @RequestParam(name = "domain", required = false) String domain) throws Exception {
		Domain foundDomain = domainService.getDomainOrPrimary(domain);
		if (foundDomain == null) {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}

		User user = getUser(userId, foundDomain);
		if (user == null) {
			log.warn("could not find user: " + userId);
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}

		List<ItSystem> itSystems = null;
		if (StringUtils.hasLength(itSystemIdentifier)) {
			itSystems = getItSystems(itSystemIdentifier);
			if (itSystems.size() == 0) {
				log.warn("could not find itSystem: " + itSystemIdentifier);
				return new ResponseEntity<>(HttpStatus.NOT_FOUND);
			}
		}

		// very well, then find all of them
		if (itSystems == null) {
			itSystems = itSystemService.getAll();
		}
		
		if (itSystems != null) {
			itSystems = itSystems.stream().filter(its -> its.isAccessBlocked() == false).collect(Collectors.toList());
		}

		List<UserRole> roles = userService.getAllUserRoles(user, itSystems);
		
		if (roles != null) {
			for (UserRole role : roles) {
				if (role.getId() == roleId) {
					return new ResponseEntity<>("Found role", HttpStatus.OK);
				}
			}
		}

		return new ResponseEntity<>("Did not find role", HttpStatus.NOT_FOUND);
	}
	
	@RequestMapping(value = "/api/user/{userid}/hasSystemRole", method = RequestMethod.GET)
	public ResponseEntity<String> userHasSystemRole(@PathVariable("userid") String userId, @RequestParam(name = "roleIdentifier") String roleIdentifier, @RequestParam(name = "system", required = false) String itSystemIdentifier, @RequestParam(name = "domain", required = false) String domain) throws Exception {
		Domain foundDomain = domainService.getDomainOrPrimary(domain);
		if (foundDomain == null) {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}

		User user = getUser(userId, foundDomain);
		if (user == null) {
			log.warn("could not find user: " + userId);
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}

		List<ItSystem> itSystems = null;
		if (StringUtils.hasLength(itSystemIdentifier)) {
			itSystems = getItSystems(itSystemIdentifier);
			if (itSystems.size() == 0) {
				log.warn("could not find itSystem: " + itSystemIdentifier);
				return new ResponseEntity<>(HttpStatus.NOT_FOUND);
			}
		}

		// very well, then find all of them
		if (itSystems == null) {
			itSystems = itSystemService.getAll();
		}

		if (itSystems != null) {
			itSystems = itSystems.stream().filter(its -> its.isAccessBlocked() == false).collect(Collectors.toList());
		}

		List<UserRole> roles = userService.getAllUserRoles(user, itSystems);
		
		if (roles != null) {
			for (UserRole role : roles) {
				for (SystemRoleAssignment assignment : role.getSystemRoleAssignments()) {
					if (assignment.getSystemRole().getIdentifier().equals(roleIdentifier)) {
						return new ResponseEntity<>("Found role", HttpStatus.OK);
					}
				}
			}
		}

		return new ResponseEntity<>("Did not find role", HttpStatus.NOT_FOUND);
	}
	
	private User getUser(String userId, Domain domain) {
		User user = userService.getByUserId(userId, domain);
		if (user == null) {
			List<User> users = userService.getByExtUuid(userId);
			if (users.size() == 1) {
				return users.get(0);
			}
		}
		
		return user;
	}
	
	private List<ItSystem> getItSystems(String itSystemIdentifier) {
		List<ItSystem> itSystems = itSystemService.findByIdentifier(itSystemIdentifier);

		if (itSystems == null || itSystems.size() == 0) {
			ItSystem itSystem = itSystemService.getByUuid(itSystemIdentifier);

			if (itSystem == null) {
				try {
					itSystem = itSystemService.getById(Long.parseLong(itSystemIdentifier));
				}
				catch (Exception ex) {
					; // ignore
				}

				return new ArrayList<>();
			}
			
			itSystems = Collections.singletonList(itSystem);
		}

		return itSystems;
	}

}
