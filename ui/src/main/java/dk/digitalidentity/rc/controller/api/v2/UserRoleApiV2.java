package dk.digitalidentity.rc.controller.api.v2;

import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.security.RequireApiReadAccessRole;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.rc.service.model.UserWithRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@RestController
@RequireApiReadAccessRole
@SecurityRequirement(name = "ApiKey")
public class UserRoleApiV2 {

	@Autowired
	private UserRoleService userRoleService;

	@Autowired
	private UserService userService;
	
	record UserRoleRecord(@Schema(description = "") long id,
			@Schema(description = "") String name,
			@Schema(description = "") String identifier,
			@Schema(description = "") String description,
			@Schema(description = "") String delegatedFromCvr,
			@Schema(description = "") boolean userOnly,
			@Schema(description = "") boolean canRequest,
			@Schema(description = "") boolean sensitiveRole) {
	}
	
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Returns a list of all userroles."),
			@ApiResponse(responseCode = "404", description ="No userroles were found.")
			
	})
	@Operation(summary = "Get all userroles.", description = "Returns all the userroles that exist.")
	@GetMapping("/api/v2/userrole")
	public ResponseEntity<List<UserRoleRecord>> getAllUserRoles(){		
		List<UserRoleRecord> result = new ArrayList<>();
		List<UserRole> userRoles = userRoleService.getAll();
		
		if (userRoles == null) {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}
		
		for (UserRole userRole : userRoles) {
			result.add(new UserRoleRecord(	userRole.getId(),userRole.getName(),userRole.getIdentifier(),
											userRole.getIdentifier(), userRole.getDelegatedFromCvr(),
											userRole.isUserOnly(),
											userRole.isCanRequest(),userRole.isSensitiveRole()));
		}

		return new ResponseEntity<>(result,HttpStatus.OK);
	}
	
	@ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Returns userrole by id."),
			@ApiResponse(responseCode = "404", description = "userrole not found.") })
	@Operation(summary = "Get userrole by ID.", description = "Returns the userrole for the specified ID.")
	@GetMapping("/api/v2/userrole/{id}")
	public ResponseEntity<UserRoleRecord> getUserRole(@Parameter(description = "The unique ID for userrole.", example="1") @PathVariable("id")long id) {
		UserRoleRecord result;
		UserRole userRole = userRoleService.getById(id);
		
		if (userRole == null) {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}
		
		result = new UserRoleRecord(userRole.getId(),userRole.getName(),userRole.getIdentifier(),
											userRole.getIdentifier(), userRole.getDelegatedFromCvr(),
											userRole.isUserOnly(),
											userRole.isCanRequest(),userRole.isSensitiveRole());

		return new ResponseEntity<>(result,HttpStatus.OK);
	}
	
	record UserRecord(@Schema(description = "ID of user") String userId,
			@Schema(description = "Name of user") String name,
			@Schema(description = "Extern ID") String extId

	) {}
	
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "All users with the specified userrole "),
			@ApiResponse(responseCode = "404", description = "No users found") })
	@Operation(summary = "Get all users with userrole by id")
	@GetMapping(value = "/api/v2/userrole/{id}/users")
	public ResponseEntity<List<UserRecord>> getUsersWithRole(@Parameter(description = "Unique ID for the userrole.", example = "1") @PathVariable("id")long id) {
		List<User> users = getAllUsersWithRoleById(id);
		if (users == null) {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}
		
		List<UserRecord> result = new ArrayList<>();
		for (User user : users) {
			result.add(new UserRecord(user.getUserId(),user.getName(),user.getExtUuid()));
		}
 		
		return new ResponseEntity<>(result,HttpStatus.OK);
	}
	
	private List<User> getAllUsersWithRoleById(long id) {
		List<User> users = new ArrayList<>();

		UserRole userRole = userRoleService.getById(id);
		if (userRole == null) {
			return users;
		}
		
		List<UserWithRole> usersWithRole = userService.getUsersWithUserRole(userRole, true);

		for (UserWithRole userWithRole : usersWithRole) {
			if (userWithRole.getUser().isDeleted() || userWithRole.getUser().isDisabled()) {
				continue;
			}

			// mere nøjagtigt tjek - Andreas
			if (users.stream().noneMatch(u -> Objects.equals(u.getUuid(), userWithRole.getUser().getUuid()))) {
				users.add(userWithRole.getUser());
			}
		}

		return users;
	}
}
