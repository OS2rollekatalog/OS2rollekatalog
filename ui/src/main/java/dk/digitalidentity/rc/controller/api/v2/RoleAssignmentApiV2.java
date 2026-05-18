package dk.digitalidentity.rc.controller.api.v2;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import dk.digitalidentity.rc.controller.api.mapper.AssignmentMapper;
import dk.digitalidentity.rc.controller.api.model.BaseOrgUnitAssignmentAM;
import dk.digitalidentity.rc.controller.api.model.OrgUnitRoleGroupAssignmentAM;
import dk.digitalidentity.rc.controller.api.model.OrgUnitUserRoleAssignmentAM;
import dk.digitalidentity.rc.controller.api.model.PostponedConstraintAM;
import dk.digitalidentity.rc.controller.api.model.TitleShallowAM;
import dk.digitalidentity.rc.controller.api.model.UserShallowAM;
import dk.digitalidentity.rc.controller.api.model.assignmentscopes.AssignmentScopeAM;
import dk.digitalidentity.rc.controller.api.model.assignmentscopes.ExceptedUserScopeAM;
import dk.digitalidentity.rc.controller.api.model.assignmentscopes.ExcludedTitleScopeAM;
import dk.digitalidentity.rc.controller.api.model.assignmentscopes.FunctionScopeAM;
import dk.digitalidentity.rc.controller.api.model.assignmentscopes.ManagerScopeAM;
import dk.digitalidentity.rc.controller.api.model.assignmentscopes.TitleScopeAM;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.OrgUnitRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.OrgUnitUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.service.OrgUnitAssignmentService;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.RoleGroupService;
import jakarta.validation.Valid;
import dk.digitalidentity.rc.service.assignment.AssignmentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import dk.digitalidentity.rc.controller.api.model.ExceptionResponseAM;
import dk.digitalidentity.rc.dao.model.ConstraintType;
import dk.digitalidentity.rc.dao.model.Domain;
import dk.digitalidentity.rc.dao.model.PostponedConstraint;
import dk.digitalidentity.rc.dao.model.SystemRole;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.security.RequireApiRoleManagementRole;
import dk.digitalidentity.rc.service.ConstraintTypeService;
import dk.digitalidentity.rc.service.DomainService;
import dk.digitalidentity.rc.service.PostponedConstraintService;
import dk.digitalidentity.rc.service.SystemRoleService;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RequestMapping(value = "/api/v2")
@RestController
@RequireApiRoleManagementRole
@SecurityRequirement(name = "ApiKey")
@RequiredArgsConstructor
@Tag(name = "Role Assignment API V2")
public class RoleAssignmentApiV2 {
    private static class ErrorMessage {
        private static final String USER_NOT_FOUND = "User not found.";
        private static final String USER_ROLE_NOT_FOUND = "User Role not found.";
    }

	private final OrgUnitAssignmentService ouAssignmentService;
	private final OrgUnitService orgUnitService;
    private final UserRoleService userRoleService;
	private final RoleGroupService roleGroupService;
	private final UserService userService;
	private final SystemRoleService systemRoleService;
	private final ConstraintTypeService constraintTypeService;
	private final DomainService domainService;
	private final PostponedConstraintService postponedConstraintService;
	private final AssignmentService assignmentService;

	@Schema(name = "UserUserRoleAssignmentRequest")
    record UserUserRoleAssignmentRecord (
    		@Schema(description = "Assignment start date") LocalDate startDate,
			@Schema(description = "Assignment end date") LocalDate stopDate,
			@Schema(description = "Assign only if the role is not already assigned") boolean onlyIfNotAssigned,
			@Schema(description = "domain") String domain,
			@Schema(description = "List of postponed constraints") List<PostponedConstraintAM> postponedConstraints) {}

	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "UserRole was successfully assigned to user"),
			@ApiResponse(responseCode = "400", description = "Requestbody was not in expected format", content =
					{ @Content(mediaType = "application/json", schema = @Schema(implementation = ExceptionResponseAM.class)) }),
			@ApiResponse(responseCode = "404", description = "Userrole, user or domain not found", content =
					{ @Content(mediaType = "application/json", schema = @Schema(implementation = ExceptionResponseAM.class)) }),
			@ApiResponse(responseCode = "500", description = "Internal Server Error", content =
					{ @Content(mediaType = "application/json", schema = @Schema(implementation = ExceptionResponseAM.class)) })
	})
	@Operation(summary = "Assign a UserRole to User.")
	@PutMapping(value = "user/{userUuid}/assign/userrole/{userRoleId}")
	public ResponseEntity<String> assignUserRoleToUser(@PathVariable("userRoleId") long userRoleId, @PathVariable("userUuid") String userUuid, @RequestBody UserUserRoleAssignmentRecord body) {
		if (body == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request must have a body.");
		}

		List<User> users = userService.getByExtUuid(userUuid);
		if (users == null || users.size() == 0) {
			users = new ArrayList<>();

			Domain foundDomain = domainService.getDomainOrPrimary(body.domain);
			if (foundDomain == null) {
				return new ResponseEntity<>("Failed to find domain with name " + body.domain, HttpStatus.NOT_FOUND);
			}

			User user = userService.getByUserId(userUuid, foundDomain);
			if (user != null) {
				users.add(user);
			}
		}

		UserRole userRole = userRoleService.getById(userRoleId);

		if (users.size() == 0) {
			return new ResponseEntity<>(ErrorMessage.USER_NOT_FOUND, HttpStatus.NOT_FOUND);
		} else if (userRole == null) {
			return new ResponseEntity<>(ErrorMessage.USER_ROLE_NOT_FOUND, HttpStatus.NOT_FOUND);
		}

		List<PostponedConstraint> resultConstaints = new ArrayList<>();

		for (PostponedConstraintAM record : body.postponedConstraints) {
			SystemRole systemRole = systemRoleService.getById(record.getSystemRoleId());
			ConstraintType constraintType = constraintTypeService.findById(record.getConstraintTypeId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "ConstraintType not found"));

			boolean found = userRole.getSystemRoleAssignments().stream().noneMatch( sra -> sra.getConstraintValues().stream().noneMatch(cv -> cv.isPostponed() && cv.getConstraintType() == constraintType));

			if (!found) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provided ConstraintType is not assigned to UserRole");
			}

			if (!postponedConstraintService.isValidConstraint(constraintType, record.getValue(), systemRole.getId())) {
			    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Constraint validation failed.");
			}

			PostponedConstraint postponedConstraint = new PostponedConstraint();
			postponedConstraint.setConstraintType(constraintType);
			postponedConstraint.setSystemRole(systemRole);
			postponedConstraint.setValue(record.getValue());
			resultConstaints.add(postponedConstraint);
		}

		for (User user : users) {
			if (body.onlyIfNotAssigned) {
				// if already assigned, skip it (note that any direct assignments with a start-date into the future will also block here)
				if (assignmentService.hasRoleDirectly(user.getUuid(), userRoleId)) {
					continue;
				}
			}

			userService.addUserRole(user, userRole, body.startDate, body.stopDate, resultConstaints);
			userService.save(user);
		}

		return new ResponseEntity<>(HttpStatus.OK);
	}

	private record OuAssignmentContext(Set<String> exceptedUsers, Set<String> titles, Set<String> functions, boolean manager, boolean substitutes, boolean negativeTitles) {}
	@Transactional
	@Operation(summary = "Assign a UserRole to OrgUnit.")
	@PostMapping(value = "organisation/assignment/userrole")
	public ResponseEntity<OrgUnitUserRoleAssignmentAM> createUserRoleAssignment(@RequestBody @Valid final OrgUnitUserRoleAssignmentAM assignment) {
		validateScopes(assignment);
		final OrgUnit orgUnit = orgUnitService.getOptionalByUuid(assignment.getOrgUnit().getUuid())
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "OrgUnit not found"));
		final UserRole userRole = userRoleService.getOptionalById(assignment.getUserRole().getId())
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "UserRole not found"));
		final OuAssignmentContext ctx = buildContext(assignment);
		final var actualAssignment = orgUnitService.addUserRole(orgUnit, userRole, assignment.isInherit(), assignment.getStartDate(), assignment.getStopDate(), ctx.exceptedUsers, ctx.titles, ctx.negativeTitles, ctx.manager, ctx.substitutes, ctx.functions, null);
		return new ResponseEntity<>(AssignmentMapper.toAM(actualAssignment), HttpStatus.CREATED);
	}

	@Operation(summary = "Assign a RoleGroup to an OrgUnit.")
	@PostMapping(value = "organisation/assignment/rolegroup")
	public ResponseEntity<OrgUnitRoleGroupAssignmentAM> createRoleGroupAssignment(@RequestBody @Valid final OrgUnitRoleGroupAssignmentAM assignment) {
		validateScopes(assignment);
		final OrgUnit orgUnit = orgUnitService.getOptionalByUuid(assignment.getOrgUnit().getUuid())
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "OrgUnit not found"));
		final RoleGroup roleGroup = roleGroupService.getOptionalById(assignment.getRoleGroup().getId())
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "RoleGroup not found"));
		final OuAssignmentContext ctx = buildContext(assignment);
		final var actualAssignment = orgUnitService.addRoleGroup(orgUnit, roleGroup, assignment.isInherit(), assignment.getStartDate(), assignment.getStopDate(), ctx.exceptedUsers, ctx.titles, ctx.negativeTitles, ctx.manager, ctx.substitutes, ctx.functions, null);
		return new ResponseEntity<>(AssignmentMapper.toAM(actualAssignment), HttpStatus.CREATED);
	}

	@Transactional(readOnly = true)
	@Operation(summary = "Get an OrgUnit UserRole assignment")
	@GetMapping(value = "organisation/assignment/userrole/{assignmentId}")
	public ResponseEntity<OrgUnitUserRoleAssignmentAM> getOrgUnitUserRoleAssignment(@PathVariable final Long assignmentId) {
		final OrgUnitUserRoleAssignment assignment = ouAssignmentService.getOrgUnitUserRoleAssignment(assignmentId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		return new ResponseEntity<>(AssignmentMapper.toAM(assignment), HttpStatus.OK);
	}


	@Transactional(readOnly = true)
	@Operation(summary = "Get an OrgUnit RoleGroup assignment")
	@GetMapping(value = "organisation/assignment/rolegroup/{assignmentId}")
	public ResponseEntity<OrgUnitRoleGroupAssignmentAM> getOrgUnitRoleGroupAssignment(@PathVariable final Long assignmentId) {
		OrgUnitRoleGroupAssignment assignment = ouAssignmentService.getOrgUnitRoleGroupAssignment(assignmentId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		return new ResponseEntity<>(AssignmentMapper.toAM(assignment), HttpStatus.OK);
	}

	@Transactional
	@Operation(summary = "Update a OrgUnit UserRole assignment")
	@PutMapping(value = "organisation/assignment/userrole/{assignmentId}")
	public ResponseEntity<OrgUnitUserRoleAssignmentAM> updateUserRoleAssignment(@PathVariable final Long assignmentId,
																				@RequestBody @Valid final OrgUnitUserRoleAssignmentAM assignment) {
		validateScopes(assignment);
		final OrgUnitUserRoleAssignment dbAssignment = ouAssignmentService.getOrgUnitUserRoleAssignment(assignmentId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		if (!dbAssignment.getOrgUnit().getUuid().equalsIgnoreCase(assignment.getOrgUnit().getUuid())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Changing OrgUnit UUID not supported.");
		}
		final OuAssignmentContext ctx = buildContext(assignment);
		orgUnitService.updateUserRoleAssignment(dbAssignment.getOrgUnit(), dbAssignment, assignment.isInherit(),
			assignment.getStartDate(), assignment.getStopDate(), ctx.exceptedUsers, ctx.titles, ctx.negativeTitles, ctx.manager, ctx.substitutes, ctx.functions);
		return new ResponseEntity<>(assignment, HttpStatus.NO_CONTENT);
	}

	@Transactional
	@Operation(summary = "Update a RoleGroup assignment to OrgUnit.")
	@PutMapping(value = "organisation/assignment/rolegroup/{assignmentId}")
	public ResponseEntity<OrgUnitRoleGroupAssignmentAM> updateRoleGroupAssignment(@PathVariable final Long assignmentId,
																				  @RequestBody @Valid final OrgUnitRoleGroupAssignmentAM assignment) {
		validateScopes(assignment);
		final OrgUnitRoleGroupAssignment dbAssignment = ouAssignmentService.getOrgUnitRoleGroupAssignment(assignmentId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		if (!dbAssignment.getOrgUnit().getUuid().equalsIgnoreCase(assignment.getOrgUnit().getUuid())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Changing OrgUnit UUID not supported.");
		}
		final OuAssignmentContext ctx = buildContext(assignment);
		orgUnitService.updateRoleGroupAssignment(dbAssignment.getOrgUnit(), dbAssignment, assignment.isInherit(),
			assignment.getStartDate(), assignment.getStopDate(), ctx.exceptedUsers, ctx.titles, ctx.negativeTitles, ctx.manager, ctx.substitutes, ctx.functions);
		return new ResponseEntity<>(assignment, HttpStatus.NO_CONTENT);
	}

	@Transactional
	@Operation(summary = "Delete an UserRole assignment from OrgUnit.")
	@DeleteMapping(value = "organisation/assignment/userrole/{assignmentId}")
	public ResponseEntity<Void> deleteUserRoleAssignment(@PathVariable final Long assignmentId) {
		final OrgUnitUserRoleAssignment assignment = ouAssignmentService.getOrgUnitUserRoleAssignment(assignmentId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		orgUnitService.removeUserRoleAssignment(assignment.getOrgUnit(), assignment);
		return new ResponseEntity<>(HttpStatus.NO_CONTENT);
	}

	@Transactional
	@Operation(summary = "Delete an RoleGroup assignment from OrgUnit.")
	@DeleteMapping(value = "organisation/assignment/rolegroup/{assignmentId}")
	public ResponseEntity<Void> deleteRoleGroupAssignment(@PathVariable final Long assignmentId) {
		final OrgUnitRoleGroupAssignment assignment = ouAssignmentService.getOrgUnitRoleGroupAssignment(assignmentId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		orgUnitService.removeRoleGroupAssignment(assignment.getOrgUnit(), assignment);
		return new ResponseEntity<>(HttpStatus.NO_CONTENT);
	}

	@Transactional(readOnly = true)
	@Operation(summary = "Get all UserRole assignments for an OrgUnit.")
	@GetMapping(value = "organisation/{orgUnitUuid}/assignment/userrole")
	public ResponseEntity<List<OrgUnitUserRoleAssignmentAM>> listUserRoleAssignments(@PathVariable final String orgUnitUuid) {
		final OrgUnit orgUnit = orgUnitService.getOptionalByUuid(orgUnitUuid)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "OrgUnit not found"));
		return new ResponseEntity<>(AssignmentMapper.toAM(orgUnit.getUserRoleAssignments()), HttpStatus.OK);
	}

	@Transactional(readOnly = true)
	@Operation(summary = "Get all RoleGroup assignments for an OrgUnit.")
	@GetMapping(value = "organisation/{orgUnitUuid}/assignment/rolegroup")
	public ResponseEntity<List<OrgUnitRoleGroupAssignmentAM>> listRoleGroupAssignments(@PathVariable final String orgUnitUuid) {
		final OrgUnit orgUnit = orgUnitService.getOptionalByUuid(orgUnitUuid)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "OrgUnit not found"));
		return new ResponseEntity<>(AssignmentMapper.roleGroupAssignmentsToAM(orgUnit.getRoleGroupAssignments()), HttpStatus.OK);
	}

	private static void validateScopes(final BaseOrgUnitAssignmentAM assignmentAM) {
		final List<AssignmentScopeAM> scopes = assignmentAM.getScopes();
		if (scopes != null && !scopes.isEmpty()) {
			if (scopes.size() > 2) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only 2 scopes are allowed.");
			}
			if (scopes.size() > 1) {
				if (!(scopes.stream().anyMatch(scope -> scope instanceof ExceptedUserScopeAM) && scopes.stream().anyMatch(scope -> scope instanceof TitleScopeAM))) {
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only ExceptedUserScope and TitleScope can be combined.");
				}
			}
		}
	}

	private OuAssignmentContext buildContext(final BaseOrgUnitAssignmentAM assignmentAM) {
		final Set<String> exceptedUsers = new HashSet<>();
		final Set<String> titles = new HashSet<>();
		final Set<String> functions = new HashSet<>();
		boolean manager = false;
		boolean substitutes = false;
		boolean negativeTitles = false;
		List<AssignmentScopeAM> scopes = assignmentAM.getScopes();
		if (scopes != null && !scopes.isEmpty()) {
			for (AssignmentScopeAM scope : scopes) {
				switch (scope) {
					case TitleScopeAM titleScopeAM -> titles.addAll(
						titleScopeAM.getTitles().stream().map(TitleShallowAM::getUuid).collect(Collectors.toSet())
					);
					case ExceptedUserScopeAM exceptedUserScopeAM -> exceptedUsers.addAll(
						exceptedUserScopeAM.getExceptedUsers().stream().map(UserShallowAM::getUuid).collect(Collectors.toSet())
					);
					case ExcludedTitleScopeAM excludedTitleScopeAM -> {
						negativeTitles = true;
						titles.addAll(
							excludedTitleScopeAM.getExcludedTitles().stream().map(TitleShallowAM::getUuid).collect(Collectors.toSet())
						);
					}
					case ManagerScopeAM managerScopeAM -> {
						manager = managerScopeAM.isManager();
						substitutes = managerScopeAM.isSubstitute();
					}
					case FunctionScopeAM functionScopeAM -> functions.addAll(functionScopeAM.getFunctions());
					default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown scope type.");
				}
			}
		}
		return new OuAssignmentContext(exceptedUsers, titles, functions, manager, substitutes, negativeTitles);
	}

}
