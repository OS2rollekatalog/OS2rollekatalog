package dk.digitalidentity.rc.controller.api.v2;

import dk.digitalidentity.rc.controller.api.mapper.RoleMapper;
import dk.digitalidentity.rc.controller.api.model.UserUserRoleAssignmentAM;
import dk.digitalidentity.rc.dao.model.Domain;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.assignment.CurrentAssignment;
import dk.digitalidentity.rc.security.RequireApiRoleManagementRole;
import dk.digitalidentity.rc.service.DomainService;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.rc.service.assignment.AssignmentService;
import dk.digitalidentity.rc.service.model.AssignedThrough;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequireApiRoleManagementRole
@SecurityRequirement(name = "ApiKey")
@Tag(name = "User API V2")
@RequiredArgsConstructor
public class UserApiV2 {
    private final ItSystemService itSystemService;
    private final DomainService domainService;
    private final UserService userService;
	private final AssignmentService assignmentService;

    @RequestMapping(value = "/api/v2/user/{userid}/assignments", method = RequestMethod.GET)
    public List<UserUserRoleAssignmentAM> getUserRolesAsList(@PathVariable("userid") final String userId,
                                                             @RequestParam(name = "system", required = false) final String itSystemIdentifier,
                                                             @RequestParam(name = "domain", required = false) final String domainArg) {
        final Domain domain = domainService.getDomainOrPrimaryOptional(domainArg)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Domain not found"));
        final User user = userService.getByUserIdOrExtUuid(userId, userId, domain)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        final List<ItSystem> itSystems = itSystemService.findByAnyIdentifier(itSystemIdentifier)
                .stream().filter(its -> !its.isAccessBlocked()).collect(Collectors.toList());

		final Set<CurrentAssignment> assignments = assignmentService.getByUserAndItSystems(user, itSystems);

		return assignments.stream()
			.map(assignment -> {
				AssignedThrough assignedThrough = assignmentService.getAssignedThrough(assignment);
				return RoleMapper.currentAssignmentToApi(assignment, user, assignedThrough);
			})
			.toList();
    }

	@Operation(summary = "Queue a user for recalculation of current role assignments.")
	@Transactional
	@RequestMapping(value = "/api/v2/user/{userid}/recalculate", method = RequestMethod.PUT)
	public void recalculateAssignments(@PathVariable("userid") final String userId,
									   @RequestParam(name = "domain", required = false) final String domainArg) {
		final Domain domain = domainService.getDomainOrPrimaryOptional(domainArg)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Domain not found"));
		final User user = userService.getByUserIdOrExtUuid(userId, userId, domain)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
		userService.queueForRecalculation(user);
	}


	@Operation(summary = "Queue all users for recalculation of current role assignments.")
	@Transactional
	@RequestMapping(value = "/api/v2/user/recalculate", method = RequestMethod.PUT)
	public void recalculateAllAssignments() {
		userService.queueAllForRecalculation();
	}

}
