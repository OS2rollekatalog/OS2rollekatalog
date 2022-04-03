package dk.digitalidentity.rc.controller.rest;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.persistence.criteria.Predicate;
import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.datatables.mapping.DataTablesInput;
import org.springframework.data.jpa.datatables.mapping.DataTablesOutput;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import dk.digitalidentity.rc.controller.mvc.datatables.dao.UserViewDao;
import dk.digitalidentity.rc.controller.mvc.datatables.dao.model.UserView;
import dk.digitalidentity.rc.controller.mvc.viewmodel.KleViewModel;
import dk.digitalidentity.rc.controller.mvc.viewmodel.UserAssignStatus;
import dk.digitalidentity.rc.controller.rest.model.PostponedConstraintDTO;
import dk.digitalidentity.rc.dao.model.ConstraintType;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.PositionRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.PositionUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.PostponedConstraint;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.SystemRole;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserKLEMapping;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.UserRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.UserUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.enums.ConstraintUIType;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import dk.digitalidentity.rc.dao.model.enums.KleType;
import dk.digitalidentity.rc.security.AccessConstraintService;
import dk.digitalidentity.rc.security.RequireAdministratorRole;
import dk.digitalidentity.rc.security.RequireAssignerRole;
import dk.digitalidentity.rc.security.RequireReadAccessOrManagerRole;
import dk.digitalidentity.rc.service.ConstraintTypeService;
import dk.digitalidentity.rc.service.KleService;
import dk.digitalidentity.rc.service.PositionService;
import dk.digitalidentity.rc.service.RoleGroupService;
import dk.digitalidentity.rc.service.SettingsService;
import dk.digitalidentity.rc.service.SystemRoleService;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.rc.service.model.AssignedThrough;
import dk.digitalidentity.rc.service.model.RoleAssignmentType;
import dk.digitalidentity.rc.service.model.RoleGroupAssignedToUser;
import dk.digitalidentity.rc.service.model.UserRoleAssignedToUser;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequireReadAccessOrManagerRole
@RestController
public class UserRestController {

    @Value("#{servletContext.contextPath}")
    private String servletContextPath;

	@Autowired
	private PositionService positionService;

	@Autowired
	private UserService userService;

	@Autowired
	private RoleGroupService roleGroupService;

	@Autowired
	private UserRoleService userRoleService;

	@Autowired
	private AccessConstraintService accessConstraintService;

	@Autowired
	private KleService kleService;
	
	@Autowired
	private SystemRoleService systemRoleService;
	
	@Autowired
	private ConstraintTypeService constraintTypeService;
	
	@Autowired
	private UserViewDao userViewDao;
	
	@Autowired
	private SettingsService settingsService;
	
	@RequireAssignerRole
	@PostMapping("/rest/users/cleanupDuplicateRoleAssignments")
	public ResponseEntity<String> cleanupDuplicateRoleAssignments() {
		userService.deleteDuplicateUserRoleAssignmentsOnUsers();
		
		return new ResponseEntity<String>(HttpStatus.OK);
	}

	@RequireAssignerRole
	@PostMapping("/rest/users/cleanupDuplicateRoleGroupAssignments")
	public ResponseEntity<String> cleanupDuplicateRoleGroupAssignments() {
		userService.deleteDuplicateRoleGroupAssignmentsOnUsers();
		
		return new ResponseEntity<String>(HttpStatus.OK);
	}
	
	@RequireAdministratorRole
	@PostMapping("/rest/users/loadcics")
	public ResponseEntity<HttpStatus> loadCics() {
		settingsService.setRunCics(true);
		
		return new ResponseEntity<>(HttpStatus.OK);
	}
	
	@PostMapping("/rest/users/list")
	public DataTablesOutput<UserView> list(@Valid @RequestBody DataTablesInput input, BindingResult bindingResult, Locale locale) {

		if (bindingResult.hasErrors()) {
			DataTablesOutput<UserView> error = new DataTablesOutput<>();
			error.setError(bindingResult.toString());

			return error;
		}

		List<String> constrainedOrgUnitUuids = accessConstraintService.getConstrainedOrgUnits(false);

		if (constrainedOrgUnitUuids == null) {
			return userViewDao.findAll(input);
		}
		else if (constrainedOrgUnitUuids.isEmpty()) {
			return new DataTablesOutput<>();
		}
		else {
			return userViewDao.findAll(input, getUserByOrgUnitUuidIn(constrainedOrgUnitUuids));
		}
	}
	
	private Specification<UserView> getUserByOrgUnitUuidIn(List<String> orgUnitUuids) {
		
		// SELECT * FROM "view" WHERE orgunitUuid LIKE ('') OR orunitUuid LIKE ('') OR orgunitUuid LIKE ('') ...
		Specification<UserView> specification = null;
		specification = (Specification<UserView>) (root, query, criteriaBuilder) -> {
			List<Predicate> predicates = new ArrayList<>(orgUnitUuids.size());

			for (String uuid : orgUnitUuids) {
			  predicates.add(criteriaBuilder.like(root.get("orgunitUuid"), "%" + uuid + "%"));
			}

			return criteriaBuilder.or(predicates.toArray(Predicate[]::new));
		};
		
		return specification;
	}

	@RequireAssignerRole
	@PostMapping(value = "/rest/users/position/{id}/addrole/{roleid}")
	@ResponseBody
	public ResponseEntity<String> addRoleToPosition(@PathVariable("id") long positionId,
			@PathVariable("roleid") long roleId,
			@RequestParam(name = "startDate", required = false) String startDateStr,
			@RequestParam(name = "stopDate", required = false) String stopDateStr) {

		Position position = positionService.getById(positionId);
		UserRole role = userRoleService.getById(roleId);

		if (position == null || role == null) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		LocalDate startDate = null, stopDate = null;
		if (!StringUtils.isEmpty(startDateStr)) {
			try {
				startDate = LocalDate.parse(startDateStr);
			}
			catch (Exception ex) {
				log.warn("Invalid startdate string: " + startDateStr);
			}
		}
		if (!StringUtils.isEmpty(stopDateStr)) {
			try {
				stopDate = LocalDate.parse(stopDateStr);
			}
			catch (Exception ex) {
				log.warn("Invalid stopdate string: " + stopDateStr);
			}
		}

		try {
			positionService.addUserRole(position, role, startDate, stopDate);
		}
		catch (SecurityException ex) {
			return new ResponseEntity<>(ex.getMessage(), HttpStatus.FORBIDDEN);
		}

		positionService.save(position);

		return new ResponseEntity<>(HttpStatus.OK);
	}

	@RequireAssignerRole
	@PostMapping(value = "/rest/users/{uuid}/removerole/{roleid}")
	public ResponseEntity<String> removeRoleFromUser(@PathVariable("uuid") String userUuid, @PathVariable("roleid") long roleId) {
		UserRole role = userRoleService.getById(roleId);
		User user = userService.getByUuid(userUuid);
		if (user == null || role == null) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		if (role.getItSystem().getSystemType() == ItSystemType.AD && role.getItSystem().isReadonly()) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		try {
			userService.removeUserRole(user, role);

			for (Position p : user.getPositions()) {
				positionService.removeUserRole(p, role);
			}
		}
		catch (SecurityException ex) {
			return new ResponseEntity<>(ex.getMessage(), HttpStatus.FORBIDDEN);
		}
		
		userService.save(user);

		return new ResponseEntity<>(HttpStatus.OK);
	}
	
	@RequireAssignerRole
	@PostMapping(value = "/rest/users/{uuid}/addrole/{roleId}")
	@ResponseBody
	public ResponseEntity<String> addRoleToUser(@PathVariable("uuid") String userUuid,
			@PathVariable("roleId") long roleId,
			@RequestParam(name = "startDate", required = false) String startDateStr,
			@RequestParam(name = "stopDate", required = false) String stopDateStr,
			@RequestBody List<PostponedConstraintDTO> postponedConstraints) {

		User user = userService.getByUuid(userUuid);
		UserRole userRole = userRoleService.getById(roleId);
		
		if (user == null || userRole == null) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		if (userRole.getItSystem().getSystemType() == ItSystemType.AD && userRole.getItSystem().isReadonly()) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		LocalDate startDate = null, stopDate = null;
		if (!StringUtils.isEmpty(startDateStr)) {
			try {
				startDate = LocalDate.parse(startDateStr);
			}
			catch (Exception ex) {
				log.warn("Invalid startdate string: " + startDateStr);
			}
		}
		if (!StringUtils.isEmpty(stopDateStr)) {
			try {
				stopDate = LocalDate.parse(stopDateStr);
			}
			catch (Exception ex) {
				log.warn("Invalid stopdate string: " + stopDateStr);
			}
		}

		List<PostponedConstraint> postponedConstraintsForAssignment = new ArrayList<>();
		for (PostponedConstraintDTO postponedConstraintDTO : postponedConstraints) {
			SystemRole systemRole = systemRoleService.getById(postponedConstraintDTO.getSystemRoleId());
			if (systemRole == null) {
				return new ResponseEntity<>("En eller flere systemroller til udskudte dataafgrænsninger kan ikke findes", HttpStatus.BAD_REQUEST);
			}

			ConstraintType constraintType = constraintTypeService.getByUuid(postponedConstraintDTO.getConstraintTypeUuid());
			if (constraintType == null) {
				return new ResponseEntity<>("En eller flere afgrænsningstyper til udskudte dataafgrænsninger kan ikke findes", HttpStatus.BAD_REQUEST);
			}

			PostponedConstraint postponedConstraint = new PostponedConstraint();
			postponedConstraint.setConstraintType(constraintType);
			postponedConstraint.setSystemRole(systemRole);
			postponedConstraint.setValue(postponedConstraintDTO.getValue());

			postponedConstraintsForAssignment.add(postponedConstraint);
		}

		try {
			userService.addUserRole(user, userRole, startDate, stopDate, postponedConstraintsForAssignment);
		}
		catch (SecurityException ex) {
			return new ResponseEntity<>(ex.getMessage(), HttpStatus.FORBIDDEN);
		}

		userService.save(user);
		
		return new ResponseEntity<>(HttpStatus.OK);
	}

	@RequireAssignerRole
	@PostMapping(value = "/rest/users/position/{id}/addgroup/{groupid}")
	public ResponseEntity<String> addGroupToPosition(@PathVariable("id") long positionId,
			@PathVariable("groupid") long groupId,
			@RequestParam(name = "startDate", required = false) String startDateStr,
			@RequestParam(name = "stopDate", required = false) String stopDateStr) {
		Position position = positionService.getById(positionId);
		RoleGroup group = roleGroupService.getById(groupId);

		if (position == null || group == null) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}
		
		LocalDate startDate = null, stopDate = null;
		if (!StringUtils.isEmpty(startDateStr)) {
			try {
				startDate = LocalDate.parse(startDateStr);
			}
			catch (Exception ex) {
				log.warn("Invalid startdate string: " + startDateStr);
			}
		}
		if (!StringUtils.isEmpty(stopDateStr)) {
			try {
				stopDate = LocalDate.parse(stopDateStr);
			}
			catch (Exception ex) {
				log.warn("Invalid stopdate string: " + stopDateStr);
			}
		}
		
		positionService.addRoleGroup(position, group, startDate, stopDate);
		positionService.save(position);

		return new ResponseEntity<>(HttpStatus.OK);
	}

	@RequireAssignerRole
	@PostMapping(value = "/rest/users/{uuid}/removegroup/{groupid}")
	public ResponseEntity<String> removeGroupFromUser(@PathVariable("uuid") String userUuid, @PathVariable("groupid") long groupId) {
		RoleGroup group = roleGroupService.getById(groupId);
		User user = userService.getByUuid(userUuid);
		if (user == null || group == null) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		userService.removeRoleGroup(user, group);
		for (Position p : user.getPositions()) {
			positionService.removeRoleGroup(p, group);
		}
		userService.save(user);

		return new ResponseEntity<>(HttpStatus.OK);
	}

	@RequireAssignerRole
	@PostMapping(value = "/rest/users/{uuid}/removeassignment/{type}/{assignedThrough}/{assignmentId}")
	public ResponseEntity<String> removeUserRoleOrRoleGroupAssignmentFromUser(
			@PathVariable("uuid") String userUuid,
			@PathVariable("assignmentId") long assignmentId,
			@PathVariable("type") RoleAssignmentType type,
			@PathVariable("assignedThrough") AssignedThrough assignedThrough) {
		User user = userService.getByUuid(userUuid);
		if (user == null) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		if (assignedThrough == AssignedThrough.DIRECT) {
			if (type == RoleAssignmentType.USERROLE) {
				if (userService.removeUserRoleAssignment(user, assignmentId)) {
					userService.save(user);
				}
			} else if (type == RoleAssignmentType.ROLEGROUP) {
				if (userService.removeRoleGroupAssignment(user, assignmentId)) {
					userService.save(user);
				}
			}
		} else if (assignedThrough == AssignedThrough.POSITION) {
			if (type == RoleAssignmentType.USERROLE) {
				if (positionService.removeUserRoleAssignment(user, assignmentId)) {
					userService.save(user);
				}
			} else if (type == RoleAssignmentType.ROLEGROUP) {
				if (positionService.removeRoleGroupAssignment(user, assignmentId)) {
					userService.save(user);
				}
			}
		}

		return new ResponseEntity<>(HttpStatus.OK);
	}

	@RequireAssignerRole
	@PostMapping(value = "/rest/users/{uuid}/editassignment/{type}/{assignedThrough}/{assignmentId}")
	@ResponseBody
	public ResponseEntity<String> editAssignment(@PathVariable("uuid") String userUuid,
			@PathVariable("type") RoleAssignmentType type,
			@PathVariable("assignmentId") long assignmentId,
			@PathVariable("assignedThrough") AssignedThrough assignedThrough,
			@RequestParam(name = "startDate", required = false) String startDateStr,
			@RequestParam(name = "stopDate", required = false) String stopDateStr,
			@RequestBody List<PostponedConstraintDTO> postponedConstraints) {

		User user = userService.getByUuid(userUuid);

		if (user == null) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		LocalDate startDate = null, stopDate = null;
		if (!StringUtils.isEmpty(startDateStr)) {
			try {
				startDate = LocalDate.parse(startDateStr);
				
				if (startDate.equals(LocalDate.now())) {
					startDate = null;
				}
			}
			catch (Exception ex) {
				log.warn("Invalid startdate string: " + startDateStr);
			}
		}
		if (!StringUtils.isEmpty(stopDateStr)) {
			try {
				stopDate = LocalDate.parse(stopDateStr);
			}
			catch (Exception ex) {
				log.warn("Invalid stopdate string: " + stopDateStr);
			}
		}

		if (assignedThrough == AssignedThrough.DIRECT) {
			if (type == RoleAssignmentType.USERROLE) {
				UserUserRoleAssignment userRoleAssignment = user.getUserRoleAssignments().stream().filter(ura->ura.getId() == assignmentId).findAny().orElse(null);
				
				if (userRoleAssignment == null) {
					return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
				}
		
				if (userRoleAssignment.getUserRole().getItSystem().getSystemType() == ItSystemType.AD && userRoleAssignment.getUserRole().getItSystem().isReadonly()) {
					return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
				}
				
				List<PostponedConstraint> postponedConstraintsForAssignment = new ArrayList<>();
				for (PostponedConstraintDTO postponedConstraintDTO : postponedConstraints) {
					SystemRole systemRole = systemRoleService.getById(postponedConstraintDTO.getSystemRoleId());
					if (systemRole == null) {
						return new ResponseEntity<>("En eller flere systemroller til udskudte dataafgrænsninger kan ikke findes", HttpStatus.BAD_REQUEST);
					}

					ConstraintType constraintType = constraintTypeService.getByUuid(postponedConstraintDTO.getConstraintTypeUuid());
					if (constraintType == null) {
						return new ResponseEntity<>("En eller flere afgrænsningstyper til udskudte dataafgrænsninger kan ikke findes", HttpStatus.BAD_REQUEST);
					}

					PostponedConstraint postponedConstraint = new PostponedConstraint();
					postponedConstraint.setConstraintType(constraintType);
					postponedConstraint.setSystemRole(systemRole);
					postponedConstraint.setValue(postponedConstraintDTO.getValue());

					postponedConstraintsForAssignment.add(postponedConstraint);
				}
	
				try {
					userService.editUserRoleAssignment(user, userRoleAssignment, startDate, stopDate, postponedConstraintsForAssignment);
				}
				catch (SecurityException ex) {
					return new ResponseEntity<>(ex.getMessage(), HttpStatus.FORBIDDEN);
				}

				userService.save(user);
			}
			else if (type == RoleAssignmentType.ROLEGROUP) {
				UserRoleGroupAssignment roleGroupAssignment = user.getRoleGroupAssignments().stream().filter(rga -> rga.getId() == assignmentId).findAny().orElse(null);
				
				if (roleGroupAssignment == null) {
					return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
				}
				
				userService.editRoleGroupAssignment(user, roleGroupAssignment, startDate, stopDate);
				userService.save(user);
			}
		}
		else if(assignedThrough == AssignedThrough.POSITION) {
			if (type == RoleAssignmentType.USERROLE) {
				PositionUserRoleAssignment existingAssignemnt = user.getPositions().stream().map(Position::getUserRoleAssignments).flatMap(x -> x.stream()).filter(ura -> ura.getId() == assignmentId).findAny().orElse(null);
				
				if (existingAssignemnt == null) {
					return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
				}
		
				if (existingAssignemnt.getUserRole().getItSystem().getSystemType() == ItSystemType.AD && existingAssignemnt.getUserRole().getItSystem().isReadonly()) {
					return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
				}
	
				try {
					positionService.editUserRoleAssignment(user, existingAssignemnt, startDate, stopDate);
				} catch (SecurityException ex) {
					return new ResponseEntity<>(ex.getMessage(), HttpStatus.FORBIDDEN);
				}
				userService.save(user);
			} else if (type == RoleAssignmentType.ROLEGROUP) {
				PositionRoleGroupAssignment existingAssignemnt = user.getPositions().stream().map(Position::getRoleGroupAssignments).flatMap(x -> x.stream()).filter(rga -> rga.getId() == assignmentId).findAny().orElse(null);
				
				if (existingAssignemnt == null) {
					return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
				}
				
				positionService.editRoleGroupAssignment(user, existingAssignemnt, startDate, stopDate);
				userService.save(user);
			}
		}

		return new ResponseEntity<>(HttpStatus.OK);
	}
	
	@RequireAssignerRole
	@PostMapping(value = "/rest/users/{uuid}/addgroup/{groupid}")
	public ResponseEntity<String> addGroupToUser(@PathVariable("uuid") String userUuid, @PathVariable("groupid") long groupid,
			@RequestParam(name = "startDate", required = false) String startDateStr,
			@RequestParam(name = "stopDate", required = false) String stopDateStr) {
		User user = userService.getByUuid(userUuid);
		RoleGroup group = roleGroupService.getById(groupid);

		if (user == null || group==null) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}
		
		LocalDate startDate = null, stopDate = null;
		if (!StringUtils.isEmpty(startDateStr)) {
			try {
				startDate = LocalDate.parse(startDateStr);
			}
			catch (Exception ex) {
				log.warn("Invalid startdate string: " + startDateStr);
			}
		}
		if (!StringUtils.isEmpty(stopDateStr)) {
			try {
				stopDate = LocalDate.parse(stopDateStr);
			}
			catch (Exception ex) {
				log.warn("Invalid stopdate string: " + stopDateStr);
			}
		}
		
		userService.addRoleGroup(user, group, startDate, stopDate);
		userService.save(user);

		return new ResponseEntity<>(HttpStatus.OK);
	}

	@RequireAssignerRole
	@GetMapping(value = "/rest/users/assignedStatus/{objType}/{uuid}/{roleId}")
	public ResponseEntity<UserAssignStatus> alreadyAssigned(@PathVariable("objType") String objType, @PathVariable("uuid") String uuid, @PathVariable("roleId") long roleId) {
		if (objType == null || (!objType.equals("role") && !objType.equals("rolegroup"))) {
			log.warn("unknown objType: " + objType);
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);			
		}

		User user = userService.getByUuid(uuid);
		if (user == null) {
			log.warn("user does not exist: " + uuid);
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);			
		}

		boolean assignedThroughRoleGroup = false;
		boolean assignedThroughOU = false;
		boolean assignedThroughTitle = false;

		if (objType.equals("role")) {
			UserRole userRole = userRoleService.getById(roleId);
			
			if (userRole == null) {
				log.warn("userRole does not exist: " + roleId);
				return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
			}
			
			List<UserRoleAssignedToUser> assignments = userService.getAllUserRolesAssignedToUser(user, userRole.getItSystem());

			for (UserRoleAssignedToUser assigned : assignments) {
				if (assigned.getUserRole().getId() == userRole.getId()) {
					switch (assigned.getAssignedThrough()) {
						case DIRECT:
						case POSITION:
							; // no idea why anyone would ask if they already know the answer, so we ignore these cases
							break;
						case ROLEGROUP:
							assignedThroughRoleGroup = true;
							break;
						case ORGUNIT:
							assignedThroughOU = true;
							break;
						case TITLE:
							assignedThroughTitle = true;
							break;
					}
				}
			}
		}
		else {
			RoleGroup roleGroup = roleGroupService.getById(roleId);

			if (roleGroup == null) {
				log.warn("roleGroup does not exist: " + roleId);
				return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
			}
			
			List<RoleGroupAssignedToUser> assignments = userService.getAllRoleGroupsAssignedToUser(user);

			for (RoleGroupAssignedToUser assigned : assignments) {
				if (assigned.getRoleGroup().getId() == roleGroup.getId()) {
					switch (assigned.getAssignedThrough()) {
						case DIRECT:
						case POSITION:
							; // no idea why anyone would ask if they already know the answer, so we ignore these cases
							break;
						case ROLEGROUP:
							; // these do not make sense here
							break;
						case ORGUNIT:
							assignedThroughOU = true;
							break;
						case TITLE:
							assignedThroughTitle = true;
							break;
					}
				}
			}
		}

		UserAssignStatus status = new UserAssignStatus();		
		status.setSuccess(!assignedThroughRoleGroup && !assignedThroughOU && !assignedThroughTitle);
		status.setAlreadyAssignedThroughOu(assignedThroughOU);
		status.setAlreadyAssignedThroughRoleGroup(assignedThroughRoleGroup);
		status.setAlreadyAssignedThroughTitle(assignedThroughTitle);

		return new ResponseEntity<>(status, HttpStatus.OK);
	}

	// TODO: code duplication from OrgUnitRestController - move into a KleRestController
	@GetMapping(value = "/rest/users/getKle/{parentCode}", produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	public List<KleViewModel> getKle(@PathVariable("parentCode") String parentCode){
		return kleService.getKleListFromParent(parentCode);
	}

	@PostMapping(value = "/rest/users/updateAll/kle")
	@ResponseBody
	public HttpEntity<String> updateKle(@RequestHeader("uuid") String uuid, @RequestHeader("type") String type, @RequestBody List<String> codes) {
		User user = userService.getByUuid(uuid);
		if (user == null) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		KleType kleType;
		switch (type) {
			case "KlePrimary":
			case "PERFORMING":
				kleType = KleType.PERFORMING;
				break;
			case "KleSecondary":
			case "INTEREST":
				kleType = KleType.INTEREST;
				break;
			default:
				return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		Set<String> oldCodes = user.getKles().stream().filter(userKLEMapping -> userKLEMapping.getAssignmentType().equals(kleType)).map(UserKLEMapping::getCode).collect(Collectors.toSet());
		Set<String> newCodes = new HashSet<>(codes);

		//Find difference
		Set<String> intersect = new HashSet<>(oldCodes);
		intersect.retainAll(newCodes);

		oldCodes.removeAll(intersect);
		newCodes.removeAll(intersect);

		//Add / Remove differences one by one
		for (String code : oldCodes) {
			userService.removeKLE(user, kleType, code);
		}

		for (String code : newCodes) {
			userService.addKLE(user, kleType, code);
		}
		userService.save(user);

		return new ResponseEntity<>(HttpStatus.OK);
	}
	
	@RequireAssignerRole
	@PostMapping(value = "/rest/users/constraints/validate")
	@ResponseBody
	public HttpEntity<?> validatePostponedConstraints(@RequestBody List<PostponedConstraintDTO> postponedConstraints) {
		List<String> errorIds = new ArrayList<>();
		for (PostponedConstraintDTO postponedConstraintDTO : postponedConstraints) {
			SystemRole systemRole = systemRoleService.getById(postponedConstraintDTO.getSystemRoleId());
	        if (systemRole == null) {
	            return new ResponseEntity<>("En eller flere systemroller kan ikke findes", HttpStatus.BAD_REQUEST);
	        }
	        
	        ConstraintType constraintType = constraintTypeService.getByUuid(postponedConstraintDTO.getConstraintTypeUuid());
	        if (constraintType == null) {
	            return new ResponseEntity<>("En eller flere afgrænsningstyper kan ikke findes", HttpStatus.BAD_REQUEST);
	        }
	        
	        // perform regex validation (if needed)
	        if (constraintType.getUiType().equals(ConstraintUIType.REGEX) && constraintType.getRegex() != null && constraintType.getRegex().length() > 0) {
	        	try {
	        		Pattern pattern = Pattern.compile(constraintType.getRegex());
	        		Matcher matcher = pattern.matcher(postponedConstraintDTO.getValue());
	        		if (!matcher.matches()) {
	        			log.warn("Input does not match regular expression: " + postponedConstraintDTO.getValue() + " for regex: " + constraintType.getRegex());
	        			errorIds.add(postponedConstraintDTO.getSystemRoleId() + postponedConstraintDTO.getConstraintTypeUuid());
	        		}
	        	}
	        	catch (Exception ex) {
	        		log.warn("Unable to perform regex validation (giving it a free pass) on '" + constraintType.getEntityId() + "'. Message = " + ex.getMessage());
	        	}
	        }
	        
	        //Check that it has a value
	        if (StringUtils.isEmpty(postponedConstraintDTO.getValue())) {
	        	log.warn("Value is null or empty for system role id  " + postponedConstraintDTO.getSystemRoleId() + " and constraint type " + postponedConstraintDTO.getConstraintTypeUuid());
    			errorIds.add(postponedConstraintDTO.getSystemRoleId() + postponedConstraintDTO.getConstraintTypeUuid());
	        }
		}

		if (errorIds.isEmpty()) {
			return new ResponseEntity<>(HttpStatus.OK);
		} else {
			return new ResponseEntity<>(errorIds, HttpStatus.BAD_REQUEST);
		}
		
	}
}
