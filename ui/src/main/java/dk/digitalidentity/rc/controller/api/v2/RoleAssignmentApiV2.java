package dk.digitalidentity.rc.controller.api.v2;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

@RestController
@RequireApiRoleManagementRole
@SecurityRequirement(name = "ApiKey")
@RequiredArgsConstructor
@Tag(name = "Role Assignment API V2")
public class RoleAssignmentApiV2 {
    private static class ErrorMessage {
        private static String USER_NOT_FOUND = "User not found.";
        private static String USER_ROLE_NOT_FOUND = "User Role not found.";
    }

    private final UserRoleService userRoleService;
	private final UserService userService;
	private final SystemRoleService systemRoleService;
	private final ConstraintTypeService constraintTypeService;
	private final DomainService domainService;
	private final PostponedConstraintService postponedConstraintService;

	@Schema(name = "PostponedConstraint")
	record PostponedConstraintRecord(@Schema(description = "Constraint value") String value, @Schema(description = "Id of a ConstrainType")  Long constraintTypeId, @Schema(description = "Id of a SystemRole") Long systemRoleId) {
	}
	
	@Schema(name = "UserUserRoleAssignment")
    record UserUserRoleAssignmentRecord (
    		@Schema(description = "Assignment start date") LocalDate startDate,
			@Schema(description = "Assignment end date") LocalDate stopDate,
			@Schema(description = "Assign only if the role is not already assigned") boolean onlyIfNotAssigned,
			@Schema(description = "domain") String domain,
			@Schema(description = "List of postponed constraints") List<PostponedConstraintRecord> postponedConstraints) {}
	
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
	@PutMapping(value = "/api/v2/user/{userUuid}/assign/userrole/{userRoleId}")
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
		
		for (PostponedConstraintRecord record : body.postponedConstraints) {
			SystemRole systemRole = systemRoleService.getById(record.systemRoleId);
			ConstraintType constraintType = constraintTypeService.findById(record.constraintTypeId).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "ConstraintType not found"));
			
			boolean found = userRole.getSystemRoleAssignments().stream().noneMatch( sra -> sra.getConstraintValues().stream().noneMatch(cv -> cv.isPostponed() && cv.getConstraintType() == constraintType));
			
			if (!found) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provided ConstraintType is not assigned to UserRole");
			}
			
			if (!postponedConstraintService.isValidConstraint(constraintType, record.value, systemRole.getId())) {
			    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Constraint validation failed.");
			}
			
			PostponedConstraint postponedConstraint = new PostponedConstraint();
			postponedConstraint.setConstraintType(constraintType);
			postponedConstraint.setSystemRole(systemRole);
			postponedConstraint.setValue(record.value);
			resultConstaints.add(postponedConstraint);
		}

		for (User user : users) {
			if (body.onlyIfNotAssigned) {
				// if already assigned, skip it
				if (user.getUserRoleAssignments().stream().anyMatch(ura -> ura.getUserRole().getId() == userRoleId)) {
					continue;
				}
			}

			userService.addUserRole(user, userRole, body.startDate, body.stopDate, resultConstaints);
			userService.save(user);
		}

		return new ResponseEntity<>(HttpStatus.OK);
	}
	

}
