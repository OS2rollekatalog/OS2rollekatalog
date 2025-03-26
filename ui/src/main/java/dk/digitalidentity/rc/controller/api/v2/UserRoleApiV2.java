package dk.digitalidentity.rc.controller.api.v2;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.controller.api.exception.BadRequestException;
import dk.digitalidentity.rc.controller.api.mapper.RoleMapper;
import dk.digitalidentity.rc.controller.api.mapper.UserMapper;
import dk.digitalidentity.rc.controller.api.model.ExceptionResponseAM;
import dk.digitalidentity.rc.controller.api.model.SystemRoleAssignmentAM;
import dk.digitalidentity.rc.controller.api.model.UserAM2;
import dk.digitalidentity.rc.controller.api.model.UserRoleAM;
import dk.digitalidentity.rc.dao.model.ConstraintType;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.SystemRole;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignment;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignmentConstraintValue;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.enums.ConstraintValueType;
import dk.digitalidentity.rc.security.RequireApiReadAccessRole;
import dk.digitalidentity.rc.security.RequireApiRoleManagementRole;
import dk.digitalidentity.rc.service.ConstraintTypeService;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.SystemRoleService;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.rc.service.model.RoleAssignmentType;
import dk.digitalidentity.rc.service.model.UserWithRole;
import dk.digitalidentity.rc.util.IdentifierGenerator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequireApiReadAccessRole
@SecurityRequirement(name = "ApiKey")
@RequiredArgsConstructor
@Tag(name = "User-role API V2")
public class UserRoleApiV2 {
	private final UserRoleService userRoleService;
	private final UserService userService;
	private final ItSystemService itSystemService;
	private final SystemRoleService systemRoleService;
	private final ConstraintTypeService constraintTypeService;
	private final RoleCatalogueConfiguration configuration;

	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Returns a list of all userroles."),
			@ApiResponse(responseCode = "500", description = "Internal Server Error", content =
					{ @Content(mediaType = "application/json", schema = @Schema(implementation = ExceptionResponseAM.class)) })
			
	})
	@Operation(summary = "Get all userroles.", description = "Returns all the userroles that exist.")
	@GetMapping("/api/v2/userrole")
	public ResponseEntity<List<UserRoleAM>> getAllUserRoles() {
		List<UserRoleAM> result = new ArrayList<>();
		List<UserRole> userRoles = userRoleService.getAll();
		for (UserRole userRole : userRoles) {
			result.add(RoleMapper.userRoleToApi(userRole));
		}
		return new ResponseEntity<>(result,HttpStatus.OK);
	}

	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Returns userrole by id."),
			@ApiResponse(responseCode = "404", description = "Userrole not found", content =
					{@Content(mediaType = "application/json", schema = @Schema(implementation = ExceptionResponseAM.class))}),
			@ApiResponse(responseCode = "500", description = "Internal Server Error", content =
					{ @Content(mediaType = "application/json", schema = @Schema(implementation = ExceptionResponseAM.class)) })
	})
	@Operation(summary = "Get userrole by ID.")
	@GetMapping("/api/v2/userrole/{id}")
	public ResponseEntity<UserRoleAM> getUserRole(@Parameter(description = "The unique ID for userrole.", example="1", required = true) @PathVariable("id") long id) {
		final UserRole userRole = userRoleService.getOptionalById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		return new ResponseEntity<>(RoleMapper.userRoleToApi(userRole),HttpStatus.OK);
	}
	
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "All users with the specified userrole "),
			@ApiResponse(responseCode = "404", description = "Userrole not found", content =
					{ @Content(mediaType = "application/json", schema = @Schema(implementation = ExceptionResponseAM.class)) }),
			@ApiResponse(responseCode = "500", description = "Internal Server Error", content =
					{ @Content(mediaType = "application/json", schema = @Schema(implementation = ExceptionResponseAM.class)) })
	})
	@Operation(summary = "Get all users with userrole id")
	@GetMapping(value = "/api/v2/userrole/{id}/users")
	public ResponseEntity<List<UserAM2>> getUsersWithRole(@Parameter(description = "Unique ID for the userrole.", example = "1") @PathVariable("id")long id) {
		final List<UserAM2> result = getAllUsersWithRoleById(id).stream()
				.map(UserMapper::toApi)
				.collect(Collectors.toList());
		return new ResponseEntity<>(result,HttpStatus.OK);
	}

	@ApiResponses(value = {
			@ApiResponse(responseCode = "204", description = "Userrole was deleted"),
			@ApiResponse(responseCode = "404", description = "Userrole not found", content =
					{ @Content(mediaType = "application/json", schema = @Schema(implementation = ExceptionResponseAM.class)) }),
			@ApiResponse(responseCode = "500", description = "Internal Server Error", content =
					{ @Content(mediaType = "application/json", schema = @Schema(implementation = ExceptionResponseAM.class)) })
	})
	@Operation(summary = "Deletes userrole with the associated id")
	@DeleteMapping(value = "/api/v2/userrole/{id}")
	@RequireApiRoleManagementRole
	@Transactional
	public ResponseEntity<?> deleteRole(@Parameter(description = "Unique ID for the userrole.", example = "1") @PathVariable long id) {
		UserRole userRole = userRoleService.getOptionalById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		userRoleService.delete(userRole);
		return new ResponseEntity<>(HttpStatus.NO_CONTENT);

	}

	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "UserRole was successfully updated"),
			@ApiResponse(responseCode = "400", description = "Requestbody was not in expected format", content =
					{ @Content(mediaType = "application/json", schema = @Schema(implementation = ExceptionResponseAM.class)) }),
			@ApiResponse(responseCode = "409", description = "The ID in the requestbody did not match that of they url.", content =
					{ @Content(mediaType = "application/json", schema = @Schema(implementation = ExceptionResponseAM.class)) }),
			@ApiResponse(responseCode = "404", description = "Userrole or it-system not found", content =
					{ @Content(mediaType = "application/json", schema = @Schema(implementation = ExceptionResponseAM.class)) }),
			@ApiResponse(responseCode = "500", description = "Internal Server Error", content =
					{ @Content(mediaType = "application/json", schema = @Schema(implementation = ExceptionResponseAM.class)) })
	})
	@Operation(summary = "Update a userrole with the data specified in the requestbody, does not support partial update, all values should be included")
	@PutMapping(value = "/api/v2/userrole/{id}")
	@RequireApiRoleManagementRole
	@Transactional
	public ResponseEntity<?> updateRole(@PathVariable("id") final long roleId,
										@RequestBody @Valid UserRoleAM userRoleDTO) {
		if (userRoleDTO.getId() != null && userRoleDTO.getId() != roleId) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Id mismatch");
		}
		UserRole userRole = userRoleService.getOptionalById(roleId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		ItSystem itSystem = itSystemService.getOptionalById(userRoleDTO.getItSystemId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "It System was not found"));
		validateUserRoleRequest(itSystem, userRoleDTO);

		setUserRoleProperties(userRoleDTO, itSystem, userRole);

		userRoleService.save(userRole);
		return new ResponseEntity<>(HttpStatus.OK);
	}

	@ApiResponses(value = {
			@ApiResponse(responseCode = "201", description = "Successfully created new UserRole"),
			@ApiResponse(responseCode = "400", description = "Requestbody was not in expected format", content =
					{ @Content(mediaType = "application/json", schema = @Schema(implementation = ExceptionResponseAM.class)) }),
			@ApiResponse(responseCode = "404", description = "It-system not found", content =
					{ @Content(mediaType = "application/json", schema = @Schema(implementation = ExceptionResponseAM.class)) }),
			@ApiResponse(responseCode = "500", description = "Internal Server Error", content =
					{ @Content(mediaType = "application/json", schema = @Schema(implementation = ExceptionResponseAM.class)) })
	})
	@Operation(summary = "Creates a userrole with the data specified in the requestbody")
	@PostMapping(value = "/api/v2/userrole")
	@RequireApiRoleManagementRole
	@Transactional
	public ResponseEntity<UserRoleAM> createRole(@RequestBody @Valid UserRoleAM userRoleDTO) {
		ItSystem itSystem = itSystemService.getOptionalById(userRoleDTO.getItSystemId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "It System was not found"));
		validateUserRoleRequest(itSystem, userRoleDTO);

		UserRole target = new UserRole();
		target.setItSystem(itSystem);
		final UserRole userRole = setUserRoleProperties(userRoleDTO, itSystem, target);
		final UserRole result = userRoleService.save(userRole);
		return new ResponseEntity<>(RoleMapper.userRoleToApi(result), HttpStatus.CREATED);
	}

	private void validateUserRoleRequest(final ItSystem itSystem, final UserRoleAM userRoleDTO) {
		if (userRoleDTO.getSystemRoleAssignments() == null) {
			return;
		}
		userRoleDTO.getSystemRoleAssignments().stream()
				.map(systemRoleAssignmentApiDTO -> systemRoleService.getOptionalById(systemRoleAssignmentApiDTO.getSystemRoleId())
                        .orElseThrow(() -> new BadRequestException("System role not found")))
				.forEach(s -> {
					if (s.getItSystem().getId() != itSystem.getId()) {
						throw new BadRequestException("Can only add system roles from same it-system");
					}
				});
	}

	private UserRole setUserRoleProperties(final UserRoleAM userRoleAM, final ItSystem itSystem, final UserRole target) {
		final Set<Long> currentSystemRoleIds = target.getSystemRoleAssignments() != null
				? target.getSystemRoleAssignments().stream()
						.map(sa -> sa.getSystemRole().getId())
						.collect(Collectors.toSet())
				: Collections.emptySet();
		final Set<Long> wanedSystemRoleIds = userRoleAM.getSystemRoleAssignments() != null
				? userRoleAM.getSystemRoleAssignments().stream().map(SystemRoleAssignmentAM::getSystemRoleId).collect(Collectors.toSet())
				: Collections.emptySet();
		final Set<Long> toRemove = new HashSet<>(currentSystemRoleIds);
		toRemove.removeAll(wanedSystemRoleIds);
		final Set<Long> toAdd = new HashSet<>(wanedSystemRoleIds);
		toAdd.removeAll(currentSystemRoleIds);
		if (target.getSystemRoleAssignments() == null) {
			target.setSystemRoleAssignments(new ArrayList<>());
		}

		target.setName(userRoleAM.getName());
		target.setIdentifier(userRoleAM.getIdentifier());
		target.setDescription(userRoleAM.getDescription());
		target.setDelegatedFromCvr(userRoleAM.getDelegatedFromCvr());
		target.setUserOnly(userRoleAM.isUserOnly());
		target.setCanRequest(userRoleAM.isCanRequest());
		target.setSensitiveRole(userRoleAM.isSensitiveRole());
		target.setItSystem(itSystem);

		toRemove.stream()
				.map(systemId -> target.getSystemRoleAssignments().stream()
						.filter(a -> systemId.equals(a.getSystemRole().getId()))
						.findFirst()
						.orElse(null)
				)
				.filter(Objects::nonNull)
				.forEach(systemRoleAssignment -> userRoleService.removeSystemRoleAssignment(target, systemRoleAssignment));
		toAdd.forEach(systemRoleId -> userRoleService.addSystemRoleAssignment(target, createAssignment(target, systemRoleId)));
		final UserRole savedTarget = userRoleService.save(target);
		updateConstraints(savedTarget, userRoleAM);

		return savedTarget;
	}

	private void updateConstraints(final UserRole userRole, final UserRoleAM userRoleAM) {
		if (userRoleAM.getSystemRoleAssignments() == null) {
			return; // Do not update if element was missing
		}
		userRoleAM.getSystemRoleAssignments().forEach(systemRoleAssignmentAM -> {
				final SystemRoleAssignment assignment = findSystemRoleAssignment(userRole, systemRoleAssignmentAM)
						.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "System role assignment not found"));
				final List<SystemRoleAssignmentConstraintValue> constraintValues = systemRoleAssignmentAM.getConstraintValues()
						.stream().map(c -> {
							final ConstraintType constraintType = constraintTypeService.findById(c.getConstraintTypeId())
									.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Constraint type not found"));
							final SystemRoleAssignmentConstraintValue value = new SystemRoleAssignmentConstraintValue();
							value.setConstraintValue(c.getConstraintValue());
							value.setConstraintValueType(ConstraintValueType.valueOf(c.getConstraintValueType().name()));
							value.setConstraintIdentifier(c.getConstraintIdentifier());
							value.setPostponed(c.isPostponed());
							value.setSystemRoleAssignment(assignment);
							value.setConstraintType(constraintType);
							if (StringUtils.isEmpty(value.getConstraintIdentifier())) {
								value.setConstraintIdentifier(IdentifierGenerator.buildKombitConstraintIdentifier(
										configuration.getIntegrations().getKombit().getDomain(),
										assignment.getSystemRole(),
										assignment,
										constraintType
								));
							}
							return value;
						})
						.toList();
				assignment.getConstraintValues().clear();
				assignment.getConstraintValues().addAll(constraintValues);
			});
	}

	private Optional<SystemRoleAssignment> findSystemRoleAssignment(final UserRole userRole, final SystemRoleAssignmentAM systemRoleAssignmentAM) {
		return userRole.getSystemRoleAssignments().stream()
				.filter(s -> s.getSystemRole().getId() == systemRoleAssignmentAM.getSystemRoleId())
				.findFirst();
	}

	private SystemRoleAssignment createAssignment(UserRole userRole, Long systemRoleId) {
		SystemRole systemRole = systemRoleService.getOptionalById(systemRoleId)
				.orElseThrow(() -> new BadRequestException("System role noget found"));
		SystemRoleAssignment systemRoleAssignment = new SystemRoleAssignment();
		systemRoleAssignment.setAssignedByName("Systembruger");
		systemRoleAssignment.setAssignedByUserId("Systembruger");
		systemRoleAssignment.setAssignedTimestamp(new Date());
		systemRoleAssignment.setSystemRole(systemRole);
		systemRoleAssignment.setUserRole(userRole);
		systemRoleAssignment.setConstraintValues(new ArrayList<>());
		return systemRoleAssignment;
	}

	private List<User> getAllUsersWithRoleById(long id) {
		List<User> users = new ArrayList<>();
		UserRole userRole = userRoleService.getOptionalById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User role not found"));
		List<UserWithRole> usersWithRole = userService.getUsersWithUserRole(userRole, true);
		for (UserWithRole userWithRole : usersWithRole) {
			if (userWithRole.getUser().isDeleted() || userWithRole.getUser().isDisabled()) {
				continue;
			}
			if (users.stream().noneMatch(u -> Objects.equals(u.getUuid(), userWithRole.getUser().getUuid()))) {
				users.add(userWithRole.getUser());
			}
		}
		return users;
	}



}
