package dk.digitalidentity.rc.controller.api.v2;

import dk.digitalidentity.rc.controller.api.mapper.RoleMapper;
import dk.digitalidentity.rc.controller.api.model.UserRoleAM;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.SystemRole;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import dk.digitalidentity.rc.security.RequireApiItSystemRole;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@RestController
@RequireApiItSystemRole
@SecurityRequirement(name = "ApiKey")
@Tag(name = "IT-system API V2")
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
			@Schema(description = "True for IT-Systems used for assigning roles via the V2 API") boolean apiManagedRoleAssignments) {
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
			result.add(new ItSystemRecord(itSystem.getId(), itSystem.getName(), itSystem.getIdentifier(),
					itSystem.getSystemType(), itSystem.isPaused(), itSystem.isHidden(), itSystem.isReadonly(),
					itSystem.isCanEditThroughApi(), itSystem.isDeleted(), itSystem.isAccessBlocked(),
					itSystem.isApiManagedRoleAssignments()));
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

		ItSystemRecord result = new ItSystemRecord(itSystem.getId(), itSystem.getName(), itSystem.getIdentifier(),
				itSystem.getSystemType(), itSystem.isPaused(), itSystem.isHidden(), itSystem.isReadonly(),
				itSystem.isCanEditThroughApi(), itSystem.isDeleted(), itSystem.isAccessBlocked(),
				itSystem.isApiManagedRoleAssignments());

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
				.map(RoleMapper::toApi)
				.collect(Collectors.toList());
		return new ResponseEntity<>(result, HttpStatus.OK);
	}

	@Schema(name = "SystemRole")
	record SystemRoleRecord(@Schema(description = "Unique ID for the system role") long id,
							@Schema(description = "Name of the role") String name,
							@Schema(description = "Unique identifier of systemrole") String identifier,
							@Schema(description = "Description of systemrole") String description,
							@Schema(description = "The weight of a role, used to order hierarchical roles and only assign the strongest one (Useful in the case of different pricing tiers / Licences in a product)") int weight) {
	}

	@ApiResponses(value = { @ApiResponse(responseCode = "200", description = "All systemroles for specified it-system"),
			@ApiResponse(responseCode = "404", description = "It-system not found.") })
	@Operation(summary = "Get all systemroles by itsystem id", description = "Returns all systemroles by it-system id")
	@GetMapping("/api/v2/itsystem/{id}/systemroles")
	public ResponseEntity<List<SystemRoleRecord>> getSystemRoles(
			@Parameter(description = "Unique ID for the it-system") @PathVariable("id") long id) {

		List<SystemRoleRecord> result = new ArrayList<>();
		ItSystem itSystem = itSystemService.getById(id);
		List<SystemRole> systemRoles = systemRoleService.findByItSystem(itSystem);

		if (systemRoles == null || itSystem == null) {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}

		for (SystemRole role : systemRoles) {
			result.add(new SystemRoleRecord(role.getId(), role.getName(), role.getIdentifier(), role.getDescription(), role.getWeight()));
		}

		return new ResponseEntity<>(result, HttpStatus.OK);
	}
	
	record UserRecord(@Schema(description = "ID of user") String userId,
			@Schema(description = "Name of user") String name, @Schema(description = "Extern ID") String extId
	) {}

	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "All users with a userrole in the specified it-system"),
			@ApiResponse(responseCode = "404", description = "it-system not found.") })
	@Operation(summary = "Get all users by itsystem id", description = "Returns all users for an it-system by it-system id")
	@GetMapping(value = "/api/v2/itsystem/{id}/users")
	public ResponseEntity<List<UserRecord>> getUsersForItSystem(@Parameter(description = "Unique ID for the it-system", example = "1") @PathVariable("id") String id) {
		List<UserRecord> result = new ArrayList<UserRecord>();
		ItSystem itSystem = null;

		try {
			itSystem = itSystemService.getById(Long.parseLong(id));
		}
		catch (Exception ignored) {
			// ignore badly formatted ID
		}

		if (itSystem == null) {
			itSystem = itSystemService.getFirstByIdentifier(id);

			if (itSystem == null) {
				// we also allow looking up using UUID for KOMBIT based it-systems
				itSystem = itSystemService.getByUuid(id);

				if (itSystem == null) {
					return new ResponseEntity<>(HttpStatus.NOT_FOUND);
				}
			}
		}

		// using it-systems user roles to get all users with ANY role.
		List<User> users = getAllUsersInItSystemWithARole(itSystem);
		
		// map to record
		for (User user : users) {
			result.add(new UserRecord(user.getUserId(), user.getName(), user.getExtUuid()));
		}

		return new ResponseEntity<List<UserRecord>>(result, HttpStatus.OK);
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
