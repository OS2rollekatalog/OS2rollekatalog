package dk.digitalidentity.rc.controller.rest;

import dk.digitalidentity.rc.controller.mvc.datatables.dao.UserViewDao;
import dk.digitalidentity.rc.controller.mvc.datatables.dao.model.UserView;
import dk.digitalidentity.rc.controller.mvc.viewmodel.KleViewModel;
import dk.digitalidentity.rc.controller.mvc.viewmodel.SelectOUDTO;
import dk.digitalidentity.rc.controller.mvc.viewmodel.UserAssignStatus;
import dk.digitalidentity.rc.controller.mvc.viewmodel.UserRoleDTO;
import dk.digitalidentity.rc.controller.rest.model.ItemPermissionDTO;
import dk.digitalidentity.rc.controller.rest.model.PostponedConstraintDTO;
import dk.digitalidentity.rc.dao.model.ConstraintType;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.PostponedConstraint;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.SystemRole;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserKLEMapping;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.UserRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.UserUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.assignment.CurrentAssignment;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import dk.digitalidentity.rc.dao.model.enums.KleType;
import dk.digitalidentity.rc.security.RequireRequesterOrAssignerRole;
import dk.digitalidentity.rc.security.permission.Permission;
import dk.digitalidentity.rc.security.permission.PermissionConstraint;
import dk.digitalidentity.rc.security.permission.RequireControllerPermission;
import dk.digitalidentity.rc.security.permission.RequirePermission;
import dk.digitalidentity.rc.security.permission.Section;
import dk.digitalidentity.rc.security.permission.UserPermissionContext;
import dk.digitalidentity.rc.service.ConstraintTypeService;
import dk.digitalidentity.rc.service.KleService;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.PostponedConstraintService;
import dk.digitalidentity.rc.service.RoleGroupService;
import dk.digitalidentity.rc.service.SettingsService;
import dk.digitalidentity.rc.service.SystemRoleService;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.rc.service.assignment.AssignmentService;
import dk.digitalidentity.rc.service.model.AssignedThrough;
import dk.digitalidentity.rc.service.model.RoleAssignmentType;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@RequireControllerPermission(section = Section.USER, permission = Permission.READ)
@RestController
public class UserRestController {
	private final UserService userService;
	private final RoleGroupService roleGroupService;
	private final UserRoleService userRoleService;
	private final KleService kleService;
	private final SystemRoleService systemRoleService;
	private final ConstraintTypeService constraintTypeService;
	private final UserViewDao userViewDao;
	private final SettingsService settingsService;
	private final OrgUnitService orgUnitService;
	private final PostponedConstraintService postponedConstraintService;
	private final AssignmentService assignmentService;

	private static final Section permissionEntity = Section.USER;
	private final UserPermissionContext userPermissionContext;

	@Value("#{servletContext.contextPath}")
	private String servletContextPath;

	@RequirePermission(section = Section.USER, permission = Permission.ASSIGN)
	@PostMapping("/rest/users/cleanupDuplicateRoleAssignments")
	public ResponseEntity<String> cleanupDuplicateRoleAssignments() {
		userService.deleteDuplicateUserRoleAssignmentsOnUsers();

		return new ResponseEntity<String>(HttpStatus.OK);
	}

	@RequirePermission(section = Section.USER, permission = Permission.ASSIGN)
	@PostMapping("/rest/users/cleanupDuplicateRoleGroupAssignments")
	public ResponseEntity<String> cleanupDuplicateRoleGroupAssignments() {
		userService.deleteDuplicateRoleGroupAssignmentsOnUsers();

		return new ResponseEntity<String>(HttpStatus.OK);
	}

	@RequirePermission(section = Section.USER, permission = Permission.UPDATE)
	@PostMapping("/rest/users/loadcics")
	public ResponseEntity<HttpStatus> loadCics() {
		settingsService.setRunCics(true);

		return new ResponseEntity<>(HttpStatus.OK);
	}

	public record UserViewListDTO(String uuid, String name, String userId, String domain, String title, List<String> orgunitUuids,
								  boolean disabled, ItemPermissionDTO allowedActions) { }
	@PostMapping("/rest/users/list")
	public DataTablesOutput<UserViewListDTO> list(@Valid @RequestBody DataTablesInput input, BindingResult bindingResult, Locale locale) {

		if (bindingResult.hasErrors()) {
			DataTablesOutput<UserViewListDTO> error = new DataTablesOutput<>();
			error.setError(bindingResult.toString());

			return error;
		}

		// global search limited to name, title, userId
		String globalSearchTerm = null;
		if (input.getSearch() != null && StringUtils.hasText(input.getSearch().getValue())) {
			globalSearchTerm = input.getSearch().getValue();
		}

		PermissionConstraint constraint = userPermissionContext.getConstraint(permissionEntity, Permission.READ);

		// Build list of specifications and filter out nulls
		List<Specification<UserView>> specifications = new ArrayList<>();

		Specification<UserView> orgUnitSpec = getUserByOrgUnitUuidIn(constraint.getConstrainedOUUuids());
		if (orgUnitSpec != null) {
			specifications.add(orgUnitSpec);
		}

		Specification<UserView> searchSpec = getGlobalSearchTermPredicate(globalSearchTerm);
		if (searchSpec != null) {
			specifications.add(searchSpec);
		}

		Specification<UserView> matchAllConditions = Specification.allOf(specifications);
		DataTablesOutput<UserView> foundUsersOutput = userViewDao.findAll(input, matchAllConditions);

		return mapToOutputDTO(foundUsersOutput);
	}

	@RequirePermission(section = Section.USER, permission = Permission.ASSIGN)
	@GetMapping(value = "/rest/users/{uuid}/stale")
	public ResponseEntity<String> checkUserStale(@NotNull @PathVariable("uuid") String userUuid) {
		if (userService.isUserStale(userUuid)) {
			return ResponseEntity.status(HttpStatus.CONFLICT)
				.header("Retry-After", "1")
				.build();
		}
		return ResponseEntity.noContent().build();
	}

	@RequirePermission(section = Section.USER, permission = Permission.ASSIGN)
	@PostMapping(value = "/rest/users/{uuid}/removerole/{roleid}")
	public ResponseEntity<String> removeRoleFromUser(@PathVariable("uuid") String userUuid, @PathVariable("roleid") long roleId) {
		UserRole role = userRoleService.getById(roleId);
		User user = userService.getByUuid(userUuid);
		if (user == null || role == null) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		if (role.isReadOnly() || (role.getItSystem().getSystemType() == ItSystemType.AD && role.getItSystem().isReadonly())) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		try {
			userService.removeUserRole(user, role);
		}
		catch (SecurityException ex) {
			return new ResponseEntity<>(ex.getMessage(), HttpStatus.FORBIDDEN);
		}

		userService.save(user);

		return new ResponseEntity<>(HttpStatus.OK);
	}

	@RequirePermission(section = Section.USER, permission = Permission.ASSIGN)
	@PostMapping(value = "/rest/users/{uuid}/addrole/{roleId}")
	@ResponseBody
	public ResponseEntity<String> addRoleToUser(@PathVariable("uuid") String userUuid,
												@PathVariable("roleId") long roleId,
												@RequestParam(name = "startDate", required = false) String startDateStr,
												@RequestParam(name = "stopDate", required = false) String stopDateStr,
												@RequestParam(name = "ouuuid", required = false) String orgUnitUuid,
												@RequestParam(name = "notify", required = false) boolean shouldNotify,
												@RequestParam(name = "casenumber", required = false) String caseNumber,
												@RequestBody List<PostponedConstraintDTO> postponedConstraints) {

		User user = userService.getByUuid(userUuid);
		UserRole userRole = userRoleService.getById(roleId);
		OrgUnit orgUnit = orgUnitService.getByUuid(orgUnitUuid);

		if (user == null || userRole == null || userRole.isReadOnly()) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		if (userRole.getItSystem().getSystemType() == ItSystemType.AD && userRole.getItSystem().isReadonly()) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		LocalDate startDate = null, stopDate = null;
		if (StringUtils.hasLength(startDateStr)) {
			try {
				startDate = LocalDate.parse(startDateStr);
			} catch (Exception ex) {
				log.warn("Invalid startdate string: " + startDateStr);
			}
		}
		if (StringUtils.hasLength(stopDateStr)) {
			try {
				stopDate = LocalDate.parse(stopDateStr);
			} catch (Exception ex) {
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
			userService.addUserRole(user, userRole, startDate, stopDate, postponedConstraintsForAssignment, orgUnit, shouldNotify, caseNumber);
		} catch (SecurityException ex) {
			return new ResponseEntity<>(ex.getMessage(), HttpStatus.FORBIDDEN);
		}

		userService.save(user);

		return new ResponseEntity<>(HttpStatus.OK);
	}

	@RequirePermission(section = Section.USER, permission = Permission.ASSIGN)
	@PostMapping(value = "/rest/users/{uuid}/removegroup/{groupid}")
	public ResponseEntity<String> removeGroupFromUser(@PathVariable("uuid") String userUuid, @PathVariable("groupid") long groupId) {
		RoleGroup group = roleGroupService.getById(groupId);
		User user = userService.getByUuid(userUuid);
		if (user == null || group == null) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		userService.removeRoleGroup(user, group);
		userService.save(user);

		return new ResponseEntity<>(HttpStatus.OK);
	}

	@RequirePermission(section = Section.USER, permission = Permission.ASSIGN)
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
		}

		return new ResponseEntity<>(HttpStatus.OK);
	}

	@RequirePermission(section = Section.USER, permission = Permission.ASSIGN)
	@PostMapping(value = "/rest/users/{uuid}/editassignment/{type}/{assignedThrough}/{assignmentId}")
	@ResponseBody
	public ResponseEntity<String> editAssignment(@PathVariable("uuid") String userUuid,
												 @PathVariable("type") RoleAssignmentType type,
												 @PathVariable("assignmentId") long assignmentId,
												 @PathVariable("assignedThrough") AssignedThrough assignedThrough,
												 @RequestParam(name = "startDate", required = false) String startDateStr,
												 @RequestParam(name = "stopDate", required = false) String stopDateStr,
												 @RequestParam(name = "ouuuid", required = false) String orgUnitUuid,
												 @RequestParam(name = "casenumber", required = false) String caseNumber,
												 @RequestBody List<PostponedConstraintDTO> postponedConstraints) {

		User user = userService.getByUuid(userUuid);

		if (user == null) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		LocalDate startDate = null, stopDate = null;
		if (StringUtils.hasLength(startDateStr)) {
			try {
				startDate = LocalDate.parse(startDateStr);

				if (startDate.equals(LocalDate.now())) {
					startDate = null;
				}
			} catch (Exception ex) {
				log.warn("Invalid startdate string: " + startDateStr);
			}
		}
		if (StringUtils.hasLength(stopDateStr)) {
			try {
				stopDate = LocalDate.parse(stopDateStr);
			} catch (Exception ex) {
				log.warn("Invalid stopdate string: " + stopDateStr);
			}
		}

		if (assignedThrough == AssignedThrough.DIRECT) {
			OrgUnit orgUnit = orgUnitService.getByUuid(orgUnitUuid);

			if (type == RoleAssignmentType.USERROLE) {
				UserUserRoleAssignment userRoleAssignment = user.getUserRoleAssignments().stream().filter(ura -> ura.getId() == assignmentId).findAny().orElse(null);

				if (userRoleAssignment == null || userRoleAssignment.getUserRole().isReadOnly()) {
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
					userService.editUserRoleAssignment(user, userRoleAssignment, startDate, stopDate, postponedConstraintsForAssignment, orgUnit, caseNumber);
				} catch (SecurityException ex) {
					return new ResponseEntity<>(ex.getMessage(), HttpStatus.FORBIDDEN);
				}

				userService.save(user);
			} else if (type == RoleAssignmentType.ROLEGROUP) {
				UserRoleGroupAssignment roleGroupAssignment = user.getRoleGroupAssignments().stream().filter(rga -> rga.getId() == assignmentId).findAny().orElse(null);

				if (roleGroupAssignment == null) {
					return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
				}

				userService.editRoleGroupAssignment(user, roleGroupAssignment, startDate, stopDate, orgUnit, caseNumber);
				userService.save(user);
			}
		}

		return new ResponseEntity<>(HttpStatus.OK);
	}

	@RequirePermission(section = Section.USER, permission = Permission.ASSIGN)
	@PostMapping(value = "/rest/users/{uuid}/addgroup/{groupid}")
	public ResponseEntity<String> addGroupToUser(@PathVariable("uuid") String userUuid, @PathVariable("groupid") long groupid,
												 @RequestParam(name = "startDate", required = false) String startDateStr,
												 @RequestParam(name = "stopDate", required = false) String stopDateStr,
												 @RequestParam(name = "ouuuid", required = false) String orgUnitUuid,
												 @RequestParam(name = "casenumber", required = false) String caseNumber) {
		User user = userService.getByUuid(userUuid);
		RoleGroup group = roleGroupService.getById(groupid);
		OrgUnit orgUnit = orgUnitService.getByUuid(orgUnitUuid);

		if (user == null || group == null) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		LocalDate startDate = null, stopDate = null;
		if (StringUtils.hasLength(startDateStr)) {
			try {
				startDate = LocalDate.parse(startDateStr);
			} catch (Exception ex) {
				log.warn("Invalid startdate string: " + startDateStr);
			}
		}
		if (StringUtils.hasLength(stopDateStr)) {
			try {
				stopDate = LocalDate.parse(stopDateStr);
			} catch (Exception ex) {
				log.warn("Invalid stopdate string: " + stopDateStr);
			}
		}

		userService.addRoleGroup(user, group, startDate, stopDate, orgUnit, caseNumber);
		userService.save(user);

		return new ResponseEntity<>(HttpStatus.OK);
	}

	@RequirePermission(section = Section.USER, permission = Permission.ASSIGN)
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

			Set<CurrentAssignment> assignments = assignmentService.getByUserRoleAndUserIncludingInactive(userRole, user);

			for (CurrentAssignment assignment : assignments) {
				AssignedThrough assignedThrough = assignmentService.getAssignedThrough(assignment);
				switch (assignedThrough) {
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
		} else {
			RoleGroup roleGroup = roleGroupService.getById(roleId);

			if (roleGroup == null) {
				log.warn("roleGroup does not exist: " + roleId);
				return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
			}

			Set<CurrentAssignment> assignments = assignmentService.getByRoleGroupAndUserIncludingInactive(roleGroup, user);
			for (CurrentAssignment assignment  : assignments) {
				AssignedThrough assignedThrough = assignmentService.getAssignedThroughForRoleGroup(assignment);
				switch (assignedThrough) {
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
	public List<KleViewModel> getKle(@PathVariable("parentCode") String parentCode) {
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

	@RequireRequesterOrAssignerRole
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

			boolean isValid = postponedConstraintService.isValidConstraint(constraintType, postponedConstraintDTO.getValue(), systemRole.getId());

			if (!isValid) {
				errorIds.add(postponedConstraintDTO.getSystemRoleId() + postponedConstraintDTO.getConstraintTypeUuid());
			}
		}

		if (errorIds.isEmpty()) {
			return new ResponseEntity<>(HttpStatus.OK);
		} else {
			return new ResponseEntity<>(errorIds, HttpStatus.BAD_REQUEST);
		}

	}

	@GetMapping(value = "/rest/users/{uuid}/orgunits", produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	public List<SelectOUDTO> getUserOrgUnits(@PathVariable("uuid") String uuid) {
		User user = userService.getByUuid(uuid);
		if (user != null) {
			return orgUnitService.getOrgUnitsForUser(user).stream().map(o -> new SelectOUDTO(o)).collect(Collectors.toList());
		}

		return new ArrayList<>();
	}

	//note: properties in this DTO must have same name as in the User class, or search/filter will break
	public record AvailableUsersDTO(String uuid, String name, String userId, List<AvailableUserPositionDTO> positions,
									boolean isAlreadyAssigned, boolean disabled) {
	}

	public record AvailableUserPositionDTO(String name, AvailableUserOrgUnitDTO orgUnit) {
	}

	public record AvailableUserOrgUnitDTO(String name) {
	}

	@PostMapping("/rest/users/available/{roleId}")
	public DataTablesOutput<AvailableUsersDTO> getAvailableUsersForUserrole(
		@Valid @RequestBody DataTablesInput input,
		BindingResult bindingResult,
		@PathVariable long roleId) {

		if (bindingResult.hasErrors()) {
			return createErrorOutput(bindingResult);
		}

		UserRole role = userRoleService.getById(roleId);
		if (role == null) {
			throw new IllegalArgumentException("No userrole with id: " + roleId);
		}

		Set<CurrentAssignment> assignments = assignmentService.getActiveByUserRole(role);
		return getAvailableUsersOutput(input, assignments);
	}

	@PostMapping("/rest/users/available/rolegroup/{roleId}")
	public DataTablesOutput<AvailableUsersDTO> getAvailableUsersForRoleGroup(
		@Valid @RequestBody DataTablesInput input,
		BindingResult bindingResult,
		@PathVariable long roleId) {

		if (bindingResult.hasErrors()) {
			return createErrorOutput(bindingResult);
		}

		RoleGroup roleGroup = roleGroupService.getById(roleId);
		if (roleGroup == null) {
			throw new IllegalArgumentException("No rolegroup with id: " + roleId);
		}

		Set<CurrentAssignment> assignments = assignmentService.getActiveByRoleGroup(roleGroup);
		return getAvailableUsersOutput(input, assignments);
	}

	// Helper methods
	private DataTablesOutput<AvailableUsersDTO> createErrorOutput(BindingResult bindingResult) {
		DataTablesOutput<AvailableUsersDTO> error = new DataTablesOutput<>();
		error.setError(bindingResult.toString());
		return error;
	}

	private DataTablesOutput<AvailableUsersDTO> getAvailableUsersOutput(
		DataTablesInput input,
		Set<CurrentAssignment> assignments) {

		DataTablesOutput<User> usersFromDb = userService.getAllAsDatatableOutput(input);

		List<String> alreadyAssignedUUIDs = assignments.stream()
			.map(assignment -> assignment.getUser().getUuid())
			.toList();

		return mapToOutput(usersFromDb, alreadyAssignedUUIDs);
	}

	private DataTablesOutput<AvailableUsersDTO> mapToOutput(
		DataTablesOutput<User> usersFromDb,
		List<String> alreadyAssignedUUIDs) {

		DataTablesOutput<AvailableUsersDTO> output = new DataTablesOutput<>();
		output.setDraw(usersFromDb.getDraw());
		output.setRecordsFiltered(usersFromDb.getRecordsFiltered());
		output.setRecordsTotal(usersFromDb.getRecordsTotal());
		output.setSearchPanes(usersFromDb.getSearchPanes());
		output.setError(usersFromDb.getError());
		output.setData(usersFromDb.getData().stream()
			.map(user -> new AvailableUsersDTO(
				user.getUuid(),
				user.getName(),
				user.getUserId(),
				user.getPositions().stream()
					.map(position -> new AvailableUserPositionDTO(
						position.getName(),
						new AvailableUserOrgUnitDTO(position.getOrgUnit().getName())
					))
					.toList(),
				alreadyAssignedUUIDs.contains(user.getUuid()),
				user.isDisabled()
			))
			.toList()
		);

		return output;
	}

	@PostMapping("/rest/users/{userUuid}/available")
	public DataTablesOutput<UserRoleDTO> getUserRoleDatatableForUser(@Valid @RequestBody DataTablesInput input, BindingResult bindingResult, @PathVariable String userUuid) {

		if (bindingResult.hasErrors()) {
			DataTablesOutput<UserRoleDTO> error = new DataTablesOutput<>();
			error.setError(bindingResult.toString());
			return error;
		}

		User user = userService.getOptionalByUuid(userUuid)
				.orElseThrow(() -> new IllegalArgumentException("no user with this uuid"));

		return userRoleService.getAvailableAsDatatable(input, user);
	}


	private DataTablesOutput<UserViewListDTO> mapToOutputDTO(DataTablesOutput<UserView> datatableOutput) {
		PermissionConstraint readConstraint = userPermissionContext.getConstraint(permissionEntity, Permission.READ);
		PermissionConstraint updateConstraint = userPermissionContext.getConstraint(permissionEntity, Permission.UPDATE);
		PermissionConstraint createConstraint = userPermissionContext.getConstraint(permissionEntity, Permission.CREATE);
		PermissionConstraint deleteConstraint = userPermissionContext.getConstraint(permissionEntity, Permission.DELETE);

		DataTablesOutput<UserViewListDTO> dtoOutput = new DataTablesOutput<>();
		dtoOutput.setDraw(datatableOutput.getDraw());
		dtoOutput.setRecordsTotal(datatableOutput.getRecordsTotal());
		dtoOutput.setError(datatableOutput.getError());
		dtoOutput.setSearchPanes(datatableOutput.getSearchPanes());
		dtoOutput.setRecordsFiltered(datatableOutput.getRecordsFiltered());
		dtoOutput.setData(datatableOutput.getData().stream().map(view -> {
							ItemPermissionDTO specificAllowedActions = new ItemPermissionDTO(
									createConstraint != null && createConstraint.allowsAnyOrgunit(view.getOrgunitUuids()),
									readConstraint != null && readConstraint.allowsAnyOrgunit(view.getOrgunitUuids()),
									updateConstraint != null && updateConstraint.allowsAnyOrgunit(view.getOrgunitUuids()),
									deleteConstraint != null && deleteConstraint.allowsAnyOrgunit(view.getOrgunitUuids())
							);

							return new UserViewListDTO(
									view.getUuid(),
									view.getName(),
									view.getUserId(),
									view.getDomain(),
									view.getTitle(),
									view.getOrgunitUuids(),
									view.isDisabled(),
									specificAllowedActions
							);
						})
						.toList()
		);
		return dtoOutput;
	}

	private Specification<UserView> getUserByOrgUnitUuidIn(Set<String> orgUnitUuids) {
		if (orgUnitUuids == null ) {
			// null means all is allowed
			return null;
		}
		if (orgUnitUuids.isEmpty()) {
			// empty set means none is allowed
			return (_, _, criteriaBuilder) -> criteriaBuilder.disjunction();
		}
		return (root, query, criteriaBuilder) -> {
			// Pad feltet med ; på begge sider så vi kan søge på ;uuid;
			Expression<String> paddedField = criteriaBuilder.concat(
				criteriaBuilder.concat(
					criteriaBuilder.literal(","),
					root.get("orgunitUuids")
				),
				criteriaBuilder.literal(",")
			);
			List<Predicate> predicates = orgUnitUuids.stream()
				.map(uuid -> criteriaBuilder.like(
					paddedField,
					"%," + uuid + ",%"
				))
				.toList();
			return criteriaBuilder.or(predicates.toArray(new Predicate[0]));
		};
	}

	private Specification<UserView> getGlobalSearchTermPredicate(String globalSearchTerm) {
		return (Specification<UserView>) (root, _, criteriaBuilder) -> {
			Predicate globalPredicate = null;
			if (StringUtils.hasText(globalSearchTerm)) {
				List<Predicate> globalPredicates = new ArrayList<>(3);

				globalPredicates.add(criteriaBuilder.like(root.get("name"), "%" + globalSearchTerm + "%"));
				globalPredicates.add(criteriaBuilder.like(root.get("userId"), "%" + globalSearchTerm + "%"));
				globalPredicates.add(criteriaBuilder.like(root.get("title"), "%" + globalSearchTerm + "%"));

				globalPredicate = criteriaBuilder.or(globalPredicates.toArray(Predicate[]::new));
			}
			return globalPredicate;
		};

	}
}
