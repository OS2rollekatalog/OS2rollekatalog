package dk.digitalidentity.rc.controller.api;

import dk.digitalidentity.rc.controller.api.dto.ConstraintValue;
import dk.digitalidentity.rc.controller.api.dto.RoleAssignmentWithContraints;
import dk.digitalidentity.rc.controller.api.dto.RoleAssignmentsWithContraints;
import dk.digitalidentity.rc.controller.api.dto.UserRoleDTO;
import dk.digitalidentity.rc.controller.api.dto.read.PostponedConstraintReadDTO;
import dk.digitalidentity.rc.controller.api.dto.read.RoleGroupReadDTO;
import dk.digitalidentity.rc.controller.api.dto.read.RoleGroupWithUserRolesReadDTO;
import dk.digitalidentity.rc.controller.api.dto.read.UserReadConstraintTypeValueSetDTO;
import dk.digitalidentity.rc.controller.api.dto.read.UserReadDTO;
import dk.digitalidentity.rc.controller.api.dto.read.UserReadRoleSystemRole;
import dk.digitalidentity.rc.controller.api.dto.read.UserReadSystemRoleConstraintValue;
import dk.digitalidentity.rc.controller.api.dto.read.UserReadWrapperDTO;
import dk.digitalidentity.rc.controller.api.dto.read.UserRoleExtendedReadDTO;
import dk.digitalidentity.rc.controller.api.dto.read.UserRoleReadDTO;
import dk.digitalidentity.rc.dao.model.Domain;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.PostponedConstraint;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignment;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignmentConstraintValue;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.UserUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.enums.ConstraintValueType;
import dk.digitalidentity.rc.exceptions.OrgUnitNotFoundException;
import dk.digitalidentity.rc.security.RequireApiReadAccessRole;
import dk.digitalidentity.rc.service.DomainService;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.RoleGroupService;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.rc.service.model.Constraint;
import dk.digitalidentity.rc.service.model.PrivilegeGroup;
import dk.digitalidentity.rc.service.model.UserWithRole;
import org.modelmapper.ModelMapper;
import org.modelmapper.TypeToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@RequireApiReadAccessRole
@RestController
public class ReadOnlyApi {

	@Autowired
	private ModelMapper mapper;

	@Autowired
	private UserService userService;

	@Autowired
	private OrgUnitService orgUnitService;

	@Autowired
	private UserRoleService userRoleService;

	@Autowired
	private RoleGroupService roleGroupService;

	@Autowired
	private ItSystemService itSystemService;

	@Autowired
	private DomainService domainService;

	@GetMapping("/api/read/itsystem/roleAssignmentsWithContraints/{system}")
	public ResponseEntity<List<RoleAssignmentsWithContraints>> getRoleAssignmentsWithContraints(@PathVariable("system") String itSystemIdentifier, @RequestParam(name = "domain", required = false) String domain) {
		Domain foundDomain = domainService.getDomainOrPrimary(domain);
		if (foundDomain == null) {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}

		List<RoleAssignmentsWithContraints> result = new ArrayList<>();

		ItSystem itSystem = itSystemService.getFirstByIdentifier(itSystemIdentifier);
		if (itSystem == null) {
			// we also allow looking up using UUID for KOMBIT based it-systems
			itSystem = itSystemService.getByUuid(itSystemIdentifier);

			if (itSystem == null) {
				return new ResponseEntity<>(result, HttpStatus.NOT_FOUND);
			}
		}
		
		// find all users with a role in this system, in a HashMap to ensure single entry per user
		Map<String, User> users = new HashMap<>();
		List<UserRole> allRolesInSystem = userRoleService.getByItSystem(itSystem);
		
		for (UserRole userRole : allRolesInSystem) {
			List<UserWithRole> usersWithRole = userService.getUsersWithUserRole(userRole, true);

			for (UserWithRole userWithRole : usersWithRole) {
				users.put(userWithRole.getUser().getExtUuid(), userWithRole.getUser());
			}
		}

		for (User user : users.values()) {
			if (!Objects.equals(user.getDomain().getName(), foundDomain.getName())) {
				continue;
			}

			RoleAssignmentsWithContraints rawc = new RoleAssignmentsWithContraints();
			rawc.setExtUuid(user.getExtUuid());
			rawc.setUserId(user.getUserId());
			rawc.setAssignments(new ArrayList<>());
		
			List<PrivilegeGroup> privilegeGroups = userService.generateOIOBPPPrivileges(user, Collections.singletonList(itSystem), new HashMap<>());
			
			for (PrivilegeGroup privilegeGroup : privilegeGroups) {
				RoleAssignmentWithContraints assignment = new RoleAssignmentWithContraints();
				assignment.setRoleIdentifier(privilegeGroup.getPrivilege().getIdentifier());
				assignment.setRoleName(privilegeGroup.getPrivilege().getName());
				assignment.setRoleConstraintValues(new ArrayList<>());
				
				for (Constraint constraint : privilegeGroup.getConstraints()) {
					ConstraintValue cv = new ConstraintValue();
					cv.setConstraintType(constraint.getType());
					cv.setConstraintValues(constraint.getValue().split(","));
					
					assignment.getRoleConstraintValues().add(cv);
				}

				rawc.getAssignments().add(assignment);
			}
			
			result.add(rawc);
		}

		return new ResponseEntity<>(result, HttpStatus.OK);
	}
	
	@RequestMapping(value = "/api/read/itsystem/{system}", method = RequestMethod.GET)
	public ResponseEntity<List<UserReadWrapperDTO>> getUsersWithGivenUserRoles(@PathVariable("system") String itSystemIdentifier, @RequestParam(name = "indirectRoles", defaultValue = "false") boolean findIndirectlyAssignedRoles, @RequestParam(name = "withDescription", defaultValue = "false") boolean withDescription, @RequestParam(name = "withConstraintTypeValueSet", defaultValue = "false") boolean withConstraintTypeValueSet, @RequestParam(name = "domain", required = false) String domain, @RequestParam(name = "includePostponedConstraints", defaultValue = "false") boolean includePostponedConstraints) {
		Domain foundDomain = domainService.getDomainOrPrimary(domain);
		if (foundDomain == null) {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}

		List<UserReadWrapperDTO> result =  new ArrayList<>();

		ItSystem itSystem = itSystemService.getFirstByIdentifier(itSystemIdentifier);
		if (itSystem == null) {
			// we also allow looking up using UUID for KOMBIT based it-systems
			itSystem = itSystemService.getByUuid(itSystemIdentifier);

			if (itSystem == null) {
				try {
					itSystem = itSystemService.getById(Long.parseLong(itSystemIdentifier));
				}
				catch (Exception ex) {
					;
				}
				
				if (itSystem == null) {
					return new ResponseEntity<>(result, HttpStatus.NOT_FOUND);
				}
			}
		}

		List<UserRole> userRoles = userRoleService.getByItSystem(itSystem);
		for (UserRole userRole : userRoles) {
			UserReadWrapperDTO dto = getUsersWithUserRole(userRole, findIndirectlyAssignedRoles, withDescription, withConstraintTypeValueSet, foundDomain, includePostponedConstraints);

			result.add(dto);
		}

		return new ResponseEntity<>(result, HttpStatus.OK);
	}
	
	@RequestMapping(value = "/api/read/assigned/{id}", method = RequestMethod.GET)
	public ResponseEntity<UserReadWrapperDTO> getUsersWithGivenUserRole(@PathVariable("id") long userRoleId, @RequestParam(name = "indirectRoles", defaultValue = "false") boolean findIndirectlyAssignedRoles, @RequestParam(name = "withDescription", defaultValue = "false") boolean withDescription, @RequestParam(name = "domain", required = false) String domain) {
		UserRole userRole = userRoleService.getById(userRoleId);
		if (userRole == null) {
			return new ResponseEntity<>(new UserReadWrapperDTO(), HttpStatus.NOT_FOUND);
		}

		Domain foundDomain = domainService.getDomainOrPrimary(domain);
		if (foundDomain == null) {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}

		UserReadWrapperDTO dto = getUsersWithUserRole(userRole, findIndirectlyAssignedRoles, withDescription, false, foundDomain, false);

		return new ResponseEntity<>(dto, HttpStatus.OK);
	}

	private UserReadWrapperDTO getUsersWithUserRole(UserRole userRole, boolean indirect, boolean withDescription, boolean withConstraintTypeValueSet, Domain domain, boolean includePostponedConstraints) {
		UserReadWrapperDTO dto = new UserReadWrapperDTO();
		dto.setRoleId(userRole.getId());
		dto.setRoleIdentifier(userRole.getIdentifier());
		if (withDescription) {
			dto.setRoleDescription(userRole.getDescription());
		}
		dto.setRoleName(userRole.getName());		
		dto.setSystemRoles(new ArrayList<>());
		dto.setAssignments(new ArrayList<>());

		for (SystemRoleAssignment assignment : userRole.getSystemRoleAssignments()) {
			UserReadRoleSystemRole dtoAssignment = new UserReadRoleSystemRole();
			dtoAssignment.setRoleIdentifier(assignment.getSystemRole().getIdentifier());
			dtoAssignment.setRoleName(assignment.getSystemRole().getName());
			dtoAssignment.setRoleConstraintValues(new ArrayList<>());
			
			for (SystemRoleAssignmentConstraintValue constraintValue : assignment.getConstraintValues()) {
				UserReadSystemRoleConstraintValue dtoConstraintValue = new UserReadSystemRoleConstraintValue();
				dtoConstraintValue.setConstraintType(constraintValue.getConstraintType().getEntityId());
				if (constraintValue.getConstraintValueType().equals(ConstraintValueType.VALUE)) {
					dtoConstraintValue.setConstraintValue(constraintValue.getConstraintValue());
				}
				else {
					dtoConstraintValue.setConstraintValue("*** DYNAMIC ***");
				}
				if( withConstraintTypeValueSet ) {
					var constraintTypeValueSet = constraintValue.getConstraintType().getValueSet().stream().map(v -> new UserReadConstraintTypeValueSetDTO(v.getConstraintKey(),v.getConstraintValue())).collect(Collectors.toList());
					dtoConstraintValue.setConstraintTypeValueSet(constraintTypeValueSet);
				}
				dtoAssignment.getRoleConstraintValues().add(dtoConstraintValue);
			}
			
			dto.getSystemRoles().add(dtoAssignment);
		}

		List<UserWithRole> users = userService.getUsersWithUserRole(userRole, indirect);
		for (UserWithRole user : users) {
			if (!Objects.equals(user.getUser().getDomain().getName(), domain.getName())) {
				continue;
			}
			
			UserReadDTO userReadDto = null;
			
			// have we seen this user before?
			for (UserReadDTO readDto : dto.getAssignments()) {
				if (readDto.getUuid().equals(user.getUser().getUuid())) {
					userReadDto = readDto;
					break;
				}
			}

			List<PostponedConstraint> postponedConstraints = new ArrayList<>();
			if (includePostponedConstraints) {
				List<UserUserRoleAssignment> assignments = user.getUser().getUserRoleAssignments().stream().filter(u -> u.getUserRole().getId() == userRole.getId()).collect(Collectors.toList());
				for (UserUserRoleAssignment assignment : assignments) {
					postponedConstraints.addAll(assignment.getPostponedConstraints());
				}
			}
			
			if (userReadDto != null) {
				userReadDto.getAssignedThrough().add(user.getAssignedThrough());
			}
			else {
				userReadDto = new UserReadDTO();
				userReadDto.getAssignedThrough().add(user.getAssignedThrough());
				userReadDto.setName(user.getUser().getName());
				userReadDto.setUserId(user.getUser().getUserId());
				userReadDto.setUuid(user.getUser().getUuid());
				userReadDto.setExtUuid(user.getUser().getExtUuid());
				userReadDto.setPostponedConstraints(postponedConstraints.stream().map(p -> new PostponedConstraintReadDTO(p)).collect(Collectors.toList()));

				dto.getAssignments().add(userReadDto);
			}
		}
		
		return dto;
	}

	@RequestMapping(value = "/api/read/user/{uuid}/roles", method = RequestMethod.GET)
	public ResponseEntity<List<UserRoleReadDTO>> getUserRoles(@PathVariable("uuid") String uuid, @RequestParam(name = "domain", required = false) String domain) {
		List<UserRole> roles = new ArrayList<>();

		Domain foundDomain = domainService.getDomainOrPrimary(domain);
		if (foundDomain == null) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		roles = userService.getUserRolesAssignedDirectly(uuid, foundDomain);

		Type targetListType = new TypeToken<List<UserRoleReadDTO>>() {}.getType();
		List<UserRoleReadDTO> result = mapper.map(roles, targetListType);

		return new ResponseEntity<>(result, HttpStatus.OK);
	}

	@RequestMapping(value = "/api/read/user/{uuid}/rolegroups", method = RequestMethod.GET)
	public ResponseEntity<List<RoleGroupReadDTO>> getUserRoleGroups(@PathVariable("uuid") String uuid) {
		List<RoleGroup> roles = userService.getRoleGroupsAssignedDirectly(uuid);

		Type targetListType = new TypeToken<List<RoleGroupReadDTO>>() {}.getType();
		List<RoleGroupReadDTO> result = mapper.map(roles, targetListType);

		return new ResponseEntity<>(result, HttpStatus.OK);
	}

	@RequestMapping(value = "/api/read/ous/{uuid}/roles", method = RequestMethod.GET)
	public ResponseEntity<List<UserRoleReadDTO>> getOuRoles(@PathVariable("uuid") String uuid) {
		List<UserRole> roles = null;

		try {
			// TODO: make inherit an argument
			roles = orgUnitService.getUserRoles(uuid, false);
		}
		catch (OrgUnitNotFoundException e) {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}

		Type targetListType = new TypeToken<List<UserRoleReadDTO>>() {}.getType();
		List<UserRoleReadDTO> result = mapper.map(roles, targetListType);

		return new ResponseEntity<>(result, HttpStatus.OK);
	}
	
	@RequestMapping(value = "/api/read/ous/{uuid}/rolegroups", method = RequestMethod.GET)
	public ResponseEntity<List<RoleGroupReadDTO>> getOuRolegroups(@PathVariable("uuid") String uuid) {
		List<RoleGroup> roles = null;

		try {
			// TODO: make inherit an argument (optional, default false)
			roles = orgUnitService.getRoleGroups(uuid, false);
		}
		catch (OrgUnitNotFoundException e) {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}

		Type targetListType = new TypeToken<List<RoleGroupReadDTO>>() {}.getType();
		List<RoleGroupReadDTO> result = mapper.map(roles, targetListType);

		return new ResponseEntity<>(result, HttpStatus.OK);
	}

	@RequestMapping(value = "/api/read/rolegroups", method = RequestMethod.GET)
	public ResponseEntity<List<RoleGroupReadDTO>> list() {
		List<RoleGroup> roleGroups = roleGroupService.getAll();
		Type targetListType = new TypeToken<List<RoleGroupReadDTO>>() {}.getType();
		List<RoleGroupReadDTO> roleGroupReadDTOS = mapper.map(roleGroups, targetListType);

		return new ResponseEntity<>(roleGroupReadDTOS, HttpStatus.OK);
	}

	@RequestMapping(value = "/api/read/rolegroups/{id}", method = RequestMethod.GET)
	public ResponseEntity<RoleGroupWithUserRolesReadDTO> getRolegroupsRoles(@PathVariable("id") long rolegroupId) {
		RoleGroup roleGroup = roleGroupService.getById(rolegroupId);
		if (roleGroup == null) {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}

		RoleGroupWithUserRolesReadDTO roleGroupDTO = new RoleGroupWithUserRolesReadDTO();
		roleGroupDTO.setId(roleGroup.getId());
		roleGroupDTO.setName(roleGroup.getName());
		roleGroupDTO.setRoles(new ArrayList<>());

		List<UserRole> userRoles = roleGroup.getUserRoleAssignments().stream().map(ura -> ura.getUserRole()).collect(Collectors.toList());
		for (UserRole userRole : userRoles) {
			UserRoleReadDTO userRoleDTO = new UserRoleReadDTO(userRole);

			roleGroupDTO.getRoles().add(userRoleDTO);
		}

		return new ResponseEntity<>(roleGroupDTO, HttpStatus.OK);
	}

	@RequestMapping(value = "/api/read/userroles", method = RequestMethod.GET)
	public ResponseEntity<List<UserRoleReadDTO>> listUserRoles() {
		List<UserRole> userRoles = userRoleService.getAll();
		
		List<UserRoleReadDTO> list = new ArrayList<UserRoleReadDTO>();
		for (UserRole userRole : userRoles) {
			UserRoleReadDTO dto = new UserRoleReadDTO(userRole);
			
			list.add(dto);
		}

		return new ResponseEntity<>(list, HttpStatus.OK);
	}

	@RequestMapping(value = "/api/read/userroles/{id}", method = RequestMethod.GET)
	public ResponseEntity<UserRoleDTO> getUserRole(@PathVariable("id") long roleId) {
		UserRole role = userRoleService.getById(roleId);
		if (role == null) {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}

		UserRoleDTO roleDTO = mapper.map(role, UserRoleDTO.class);

		return new ResponseEntity<>(roleDTO, HttpStatus.OK);
	}

	@GetMapping("/api/read/userroles/itsystems")
	public ResponseEntity<List<UserRoleExtendedReadDTO>> getUserRolesByItSystems(@RequestBody(required = false) List<Long> itSystemIds) {
		List<UserRole> userRoles = null;

		if (itSystemIds == null) {
			userRoles = userRoleService.getAll();
		} else {
			for (Long itSystemId : itSystemIds) {
				if (userRoles == null) {
					userRoles = userRoleService.getByItSystemId(itSystemId);
				} else {
					userRoles.addAll(userRoleService.getByItSystemId(itSystemId));
				}
			}
		}


		Type targetListType = new TypeToken<List<UserRoleExtendedReadDTO>>() {}.getType();
		List<UserRoleExtendedReadDTO> roleDTO = mapper.map(userRoles, targetListType);

		return new ResponseEntity<>(roleDTO, HttpStatus.OK);
	}
}
