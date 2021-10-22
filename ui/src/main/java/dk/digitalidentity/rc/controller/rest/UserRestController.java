package dk.digitalidentity.rc.controller.rest;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.controller.mvc.viewmodel.KleViewModel;
import dk.digitalidentity.rc.controller.mvc.viewmodel.UserAssignStatus;
import dk.digitalidentity.rc.controller.mvc.viewmodel.UserListForm;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserKLEMapping;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import dk.digitalidentity.rc.dao.model.enums.KleType;
import dk.digitalidentity.rc.security.AccessConstraintService;
import dk.digitalidentity.rc.security.RequireAssignerRole;
import dk.digitalidentity.rc.security.RequireReadAccessOrManagerRole;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.KleService;
import dk.digitalidentity.rc.service.PositionService;
import dk.digitalidentity.rc.service.RoleGroupService;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.rc.service.model.RoleGroupAssignedToUser;
import dk.digitalidentity.rc.service.model.UserRoleAssignedToUser;
import lombok.extern.log4j.Log4j;

@Log4j
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
    private MessageSource messageSource;

	@Autowired
	private AccessConstraintService accessConstraintService;

	@Autowired
	private KleService kleService;

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
	
	@GetMapping(value = "/rest/users")
	public ResponseEntity<List<UserListForm>> getAllUsers(Locale locale) {
		boolean readOnly = !(SecurityUtil.hasRole(Constants.ROLE_ASSIGNER) || SecurityUtil.hasRole(Constants.ROLE_KLE_ADMINISTRATOR));
		
		String in = messageSource.getMessage("html.word.in", null, locale);

		List<User> users = userService.getAllThin();
		users = accessConstraintService.filterUsersUserCanAccess(users, false);

		return new ResponseEntity<>(users
				.stream()
				.map(u -> new UserListForm(u, servletContextPath, in, readOnly))
				.collect(Collectors.toList()),
				HttpStatus.OK);
	}

	@RequireAssignerRole
	@PostMapping(value = "/rest/users/position/{id}/addrole/{roleid}")
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
	public ResponseEntity<String> addRoleToUser(@PathVariable("uuid") String userUuid,
			@PathVariable("roleId") long roleId,
			@RequestParam(name = "startDate", required = false) String startDateStr,
			@RequestParam(name = "stopDate", required = false) String stopDateStr) {

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

		try {
			userService.addUserRole(user, userRole, startDate, stopDate);
		} catch (SecurityException ex) {
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
				kleType = KleType.PERFORMING;
				break;
			case "KleSecondary":
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
}
