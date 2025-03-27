package dk.digitalidentity.rc.controller.api.v2;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import dk.digitalidentity.rc.controller.api.model.ADGroupTypeAM;
import dk.digitalidentity.rc.controller.api.model.SystemRoleAM;
import dk.digitalidentity.rc.controller.api.model.UserAM2;
import dk.digitalidentity.rc.dao.model.enums.ADGroupType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import dk.digitalidentity.rc.controller.api.mapper.RoleMapper;
import dk.digitalidentity.rc.controller.api.model.UserRoleAM;
import dk.digitalidentity.rc.dao.model.Domain;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.SystemRole;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import dk.digitalidentity.rc.security.RequireApiItSystemRole;
import dk.digitalidentity.rc.service.DomainService;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.PendingADUpdateService;
import dk.digitalidentity.rc.service.SystemRoleService;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.rc.service.model.UserWithRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequireApiItSystemRole
@SecurityRequirement(name = "ApiKey")
@Tag(name = "IT-system API V2")
@Transactional
public class ItSystemApiV2 {

	@Autowired
	private ItSystemService itSystemService;

	@Autowired
	private UserRoleService userRoleService;
	
	@Autowired
	private SystemRoleService systemRoleService;
	
	@Autowired
	private UserService userService;

	@Autowired
	private PendingADUpdateService pendingADUpdateService;

	@Autowired
	private DomainService domainService;

	@Schema(name = "ItSystem")
	record ItSystemRecord(@Schema(description = "Unique ID for the it-system") long id,
			@Schema(description = "Name of the it-system") String name,
			@Schema(description = "Technical ID key for the it-system (not always unique)") String identifier,
			@Schema(description = "The type of the it-system") ItSystemType systemtype,
			@Schema(description = "Is the it-system active") boolean paused,
			@Schema(description = "Is the it-system visible") boolean hidden,
			@Schema(description = "Determines if") boolean readonly,
			@Schema(description = "Whether it-system can be edited via api") boolean canEditThroughApi,
			@Schema(description = "Is it-system deleted") boolean deleted,
			@Schema(description = "Used for blocking access to an IT-Systems associated UserRoles for any API call. No User will have roles from the IT-System assigned.") boolean accesBlocked,
			@Schema(description = "True for IT-Systems used for assigning roles via the V2 API") boolean apiManagedRoleAssignments,
			@Schema(description = "Domain name when system type is AD") String domain,
			@Schema(description = "E-mail address") String email,
			@Schema(description = "UUID of the user responsible for attestation") String responsibleUserUuid
			) {
	}

	@ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Returns the list of all it-systems."),
			@ApiResponse(responseCode = "404", description = "No it-systems were found") })
	@Operation(summary = "Get all it-systems", description = "Returns a list of all it-systems")
	@GetMapping("/api/v2/itsystem")
	public ResponseEntity<List<ItSystemRecord>> getAllItSystems() {
		List<ItSystemRecord> result = new ArrayList<>();
		List<ItSystem> itSystems = itSystemService.getAll();

		if (itSystems == null) {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}

		for (ItSystem itSystem : itSystems) {
			result.add(new ItSystemRecord(itSystem.getId(), itSystem.getName(), itSystem.getIdentifier(), itSystem.getSystemType(),
					itSystem.isPaused(), itSystem.isHidden(), itSystem.isReadonly(), itSystem.isCanEditThroughApi(),
					itSystem.isDeleted(), itSystem.isAccessBlocked(), itSystem.isApiManagedRoleAssignments(),
					(itSystem.getDomain() != null ? itSystem.getDomain().getName() : null),
					itSystem.getEmail(),
					(itSystem.getAttestationResponsible() != null ? itSystem.getAttestationResponsible().getUuid() : null)));
		}

		return new ResponseEntity<>(result, HttpStatus.OK);
	}

	@ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Returns specific it-system by id."),
			@ApiResponse(responseCode = "404", description = "It System not found.") })
	@Operation(summary = "Get it-system by id", description = "Returns the specific it-system by a given id")
	@GetMapping("/api/v2/itsystem/{id}")
	public ResponseEntity<ItSystemRecord> getItSystem(
			@Parameter(description = "Unique ID for the it-system", example = "1") @PathVariable("id") long id) {

		ItSystem itSystem = itSystemService.getById(id);
		if (itSystem == null) {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}

		ItSystemRecord result = new ItSystemRecord(itSystem.getId(), itSystem.getName(), itSystem.getIdentifier(), itSystem.getSystemType(),
				itSystem.isPaused(), itSystem.isHidden(), itSystem.isReadonly(), itSystem.isCanEditThroughApi(),
				itSystem.isDeleted(), itSystem.isAccessBlocked(), itSystem.isApiManagedRoleAssignments(),
				(itSystem.getDomain() != null ? itSystem.getDomain().getName() : null),
				itSystem.getEmail(),
				(itSystem.getAttestationResponsible() != null ? itSystem.getAttestationResponsible().getUuid() : null));

		return new ResponseEntity<>(result, HttpStatus.OK);
	}

	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "New it-system was successfuly created."),
			@ApiResponse(responseCode = "400", description = "Domain not found or Unknown system type.")
			})
	@Operation(summary = "Creates an it system with the data specified in the requestbody", description = "If specified systemType is AD the domain field is mandatory.")
	@PostMapping("/api/v2/itsystem")
	public ResponseEntity<ItSystemRecord> createItSystem(@RequestBody @Valid ItSystemRecord itSystemBody) {
		Domain domain = domainService.getByName(itSystemBody.domain);
		if (itSystemBody.systemtype == ItSystemType.AD && domain == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing Domain on creating new AD it-system");
		}

		final ItSystem newItSystem = new ItSystem();
		newItSystem.setName(itSystemBody.name);
		newItSystem.setIdentifier(itSystemBody.identifier);
		newItSystem.setEmail(itSystemBody.email);
		
		// can be null
		userService.getOptionalByUuid(itSystemBody.responsibleUserUuid).ifPresent(newItSystem::setAttestationResponsible);

		switch (itSystemBody.systemtype) {
			case AD:
				newItSystem.setSystemType(ItSystemType.AD);
				newItSystem.setPaused(true);
				newItSystem.setDomain(domain);
				break;
			case SAML:
				newItSystem.setSystemType(ItSystemType.SAML);
				break;
			case MANUAL:
				newItSystem.setSystemType(ItSystemType.MANUAL);
				break;
			default:
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown systemtype: " + itSystemBody.systemtype);
		}
		
		ItSystem savedItSystem = itSystemService.save(newItSystem);
		
		ItSystemRecord result = new ItSystemRecord(savedItSystem.getId(), savedItSystem.getName(), savedItSystem.getIdentifier(), savedItSystem.getSystemType(),
				savedItSystem.isPaused(), savedItSystem.isHidden(), savedItSystem.isReadonly(), savedItSystem.isCanEditThroughApi(),
				savedItSystem.isDeleted(), savedItSystem.isAccessBlocked(), savedItSystem.isApiManagedRoleAssignments(),
				(savedItSystem.getDomain() != null ? savedItSystem.getDomain().getName() : null),
				savedItSystem.getEmail(),
				(savedItSystem.getAttestationResponsible() != null ? savedItSystem.getAttestationResponsible().getUuid() : null));

		return new ResponseEntity<>(result, HttpStatus.OK);
	}

	@Operation(summary = "Get userroles for specific it-system by id", description = "Returns the specific it-system by a given id")
	@GetMapping("/api/v2/itsystem/{id}/userroles")
	public ResponseEntity<List<UserRoleAM>> getSystemUserRoles(
			@Parameter(description = "Unique ID for the it-system", example = "1") @PathVariable("id") Long id) {
		ItSystem itSystem = itSystemService.getById(id);
		List<UserRole> userRoles = userRoleService.getByItSystem(itSystem);

		if (itSystem == null) {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}
		List<UserRoleAM> result = userRoles.stream()
				.map(RoleMapper::userRoleToApi)
				.collect(Collectors.toList());
		return new ResponseEntity<>(result, HttpStatus.OK);
	}

	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "All systemroles for specified it-system"),
			@ApiResponse(responseCode = "404", description = "It-system not found.")
	})
	@Operation(summary = "Get all systemroles by itsystem id", description = "Returns all systemroles by it-system id")
	@GetMapping("/api/v2/itsystem/{id}/systemroles")
	public List<SystemRoleAM> getSystemRoles(
			@Parameter(description = "Unique ID for the it-system") @PathVariable("id") long id) {
		final ItSystem itSystem = itSystemService.getById(id);
		final List<SystemRole> systemRoles = systemRoleService.findByItSystem(itSystem);
		if (systemRoles == null || itSystem == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND);
		}
		return RoleMapper.systemRolesToApi(systemRoles);
	}

	@ApiResponses(value = {
			@ApiResponse(responseCode = "201", description = "System-role created"),
			@ApiResponse(responseCode = "404", description = "It-system not found.")
	})
	@Operation(summary = "Create a new system-role", description = "Returns the newly created system-role")
	@PostMapping("/api/v2/itsystem/{id}/systemroles")
	@ResponseStatus(HttpStatus.CREATED)
	public SystemRoleAM createSystemRole(@Parameter(description = "Unique ID for the it-system", example = "1") @PathVariable("id") final long itSystemId,
										 @RequestParam(value = "adGroupType", required = false, defaultValue = "NONE") final ADGroupTypeAM adGroupType,
										 @Parameter(description = "If this is an AD group, should it be universal") @RequestParam(value = "universal", required = false, defaultValue = "false") final boolean universal,
										 @RequestBody@Valid final SystemRoleAM systemRoleAM) {
		final ItSystem itSystem = itSystemService.getOptionalById(itSystemId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		final SystemRole systemRole = RoleMapper.systemRoleToEntity(systemRoleAM);
		systemRole.setItSystem(itSystem);
		final SystemRole saveSystemRole = systemRoleService.save(systemRole);
		if (adGroupType != ADGroupTypeAM.NONE) {
			pendingADUpdateService.addSystemRole(saveSystemRole, ADGroupType.valueOf(adGroupType.name()), universal);
		}

		return RoleMapper.systemRolesToApi(saveSystemRole);
	}

	@ApiResponses(value = {
			@ApiResponse(responseCode = "204", description = "System-role deleted"),
			@ApiResponse(responseCode = "404", description = "It-system or system-role not found.")
	})
	@Operation(summary = "Delete a system-role")
	@DeleteMapping("/api/v2/itsystem/{itSystemId}/systemroles/{systemRoleId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteSystemRole(@Parameter(description = "Unique ID of the it-system", example = "1") @PathVariable("itSystemId") final long itSystemId,
								 @Parameter(description = "Unique ID of the systemRole", example = "1") @PathVariable("systemRoleId") final long systemRoleId) {
		final ItSystem itSystem = itSystemService.getOptionalById(itSystemId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		systemRoleService.getOptionalById(systemRoleId)
				.ifPresentOrElse(
						s -> {
							if (itSystem.getId() != s.getItSystem().getId()) {
								throw new ResponseStatusException(HttpStatus.CONFLICT, "System role it-system doesnt match");
							}
							systemRoleService.delete(s);
						},
						() -> {
							throw new ResponseStatusException(HttpStatus.NOT_FOUND);
						}
				);
	}


	@ApiResponses(value = {
			@ApiResponse(responseCode = "204", description = "System-role updated"),
			@ApiResponse(responseCode = "404", description = "It-system or system-role not found.")
	})
	@Operation(summary = "Update an existing system-role, NOTE! constraint types cannot be update")
	@PutMapping("/api/v2/itsystem/{itSystemId}/systemroles/{systemRoleId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void updateSystemRole(@Parameter(description = "Unique ID of the it-system", example = "1") @PathVariable("itSystemId") final long itSystemId,
								 @Parameter(description = "Unique ID of the systemRole", example = "1") @PathVariable("systemRoleId") final long systemRoleId,
								 @RequestBody @Valid final SystemRoleAM systemRoleAM) {
		final ItSystem itSystem = itSystemService.getOptionalById(itSystemId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		systemRoleService.getOptionalById(systemRoleId)
				.ifPresentOrElse(
						s -> {
							if (itSystem.getId() != s.getItSystem().getId()) {
								throw new ResponseStatusException(HttpStatus.CONFLICT, "System role it-system doesnt match");
							}
							if (systemRoleAM.getName() != null) {
								s.setName(systemRoleAM.getName());
							}
							if (systemRoleAM.getDescription() != null) {
								s.setDescription(systemRoleAM.getDescription());
							}
							if (systemRoleAM.getWeight() != null) {
								s.setWeight(systemRoleAM.getWeight());
							}
						},
						() -> {
							throw new ResponseStatusException(HttpStatus.NOT_FOUND);
						}
				);
	}

	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "All users with a userrole in the specified it-system"),
			@ApiResponse(responseCode = "404", description = "it-system not found.") })
	@Operation(summary = "Get all users by itsystem id", description = "Returns all users for an it-system by it-system id")
	@GetMapping(value = "/api/v2/itsystem/{id}/users")
	public List<UserAM2> getUsersForItSystem(@Parameter(description = "Unique ID for the it-system", example = "1") @PathVariable("id") String id) {
		List<UserAM2> result = new ArrayList<UserAM2>();
		ItSystem itSystem = null;

		try {
			itSystem = itSystemService.getById(Long.parseLong(id));
		} catch (Exception ignored) {
			// ignore badly formatted ID
		}

		if (itSystem == null) {
			itSystem = itSystemService.getFirstByIdentifier(id);
			if (itSystem == null) {
				// we also allow looking up using UUID for KOMBIT based it-systems
				itSystem = itSystemService.getByUuid(id);
				if (itSystem == null) {
					throw new ResponseStatusException(HttpStatus.NOT_FOUND);
				}
			}
		}

		// using it-systems user roles to get all users with ANY role.
		List<User> users = getAllUsersInItSystemWithARole(itSystem);
		
		// map to record
		for (User user : users) {
			result.add(UserAM2.builder()
							.userId(user.getUserId())
							.extId(user.getExtUuid())
							.name(user.getName())
							.extUuid(user.getExtUuid())
					.build());
		}

		return result;
	}

	@ApiResponses(value = { @ApiResponse(responseCode = "202", description = "It-system has been marked dirty"),
			@ApiResponse(responseCode = "404", description = "It System not found.") })
	@Operation(summary = "Touch an it-system", description = "This will mark the it-system as dirty and synchronize its roles to AD")
	@PostMapping(value = "/api/v2/itsystem/{id}/touch")
	public ResponseEntity<?> touchItSystem(@Parameter(description = "Unique ID for the it-system", example = "1") @PathVariable("id") Long id) {
		final ItSystem itSystem = itSystemService.getOptionalById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		pendingADUpdateService.addItSystemToQueue(itSystem);
		return ResponseEntity.accepted().build();
	}

	private List<User> getAllUsersInItSystemWithARole(ItSystem itSystem) {
		List<User> users = new ArrayList<>();
		List<UserRole> userRoles = userRoleService.getByItSystem(itSystem);

		for (UserRole userRole : userRoles) {
			List<UserWithRole> usersWithRole = userService.getUsersWithUserRole(userRole, true);

			for (UserWithRole userWithRole : usersWithRole) {
				if (userWithRole.getUser().isDeleted() || userWithRole.getUser().isDisabled()) {
					continue;
				}

				if (users.stream().noneMatch(u -> Objects.equals(u.getUuid(), userWithRole.getUser().getUuid()))) {
					users.add(userWithRole.getUser());
				}
			}
		}
		
		return users;
	}
}
