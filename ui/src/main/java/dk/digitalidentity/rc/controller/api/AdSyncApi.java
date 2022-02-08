package dk.digitalidentity.rc.controller.api;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dk.digitalidentity.rc.controller.api.dto.ADGroupAssignments;
import dk.digitalidentity.rc.controller.api.dto.ADOperationsResult;
import dk.digitalidentity.rc.controller.api.dto.ADSyncResult;
import dk.digitalidentity.rc.dao.model.DirtyADGroup;
import dk.digitalidentity.rc.dao.model.SystemRole;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.security.RequireApiReadAccessRole;
import dk.digitalidentity.rc.service.PendingADUpdateService;
import dk.digitalidentity.rc.service.SystemRoleService;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.rc.service.model.UserWithRole;
import lombok.extern.slf4j.Slf4j;

@RequireApiReadAccessRole
@Slf4j
@RestController
public class AdSyncApi {

	@Autowired
	private PendingADUpdateService pendingADUpdateService;

	@Autowired
	private UserService userService;
	
	@Autowired
	private SystemRoleService systemRoleService;

	@GetMapping("/api/ad/v2/operations")
	public ResponseEntity<ADOperationsResult> getPendingOperations() {
		ADOperationsResult result = new ADOperationsResult();
		
		result.setOperations(pendingADUpdateService.find100Operations());

		// compute max
		long maxId = 0;
		try {
			Long maxIdCandidate = result.getOperations().stream().map(u -> u.getId()).max(Comparator.comparing(Long::valueOf)).get();
			maxId = maxIdCandidate;
		}
		catch (NoSuchElementException ex) {
			; // can be safely ignored
		}
		
		result.setHead(maxId);

		return new ResponseEntity<>(result, HttpStatus.OK);
	}
	
	@DeleteMapping("/api/ad/v2/operations/{head}")
	public ResponseEntity<String> flagOperationsPerformed(@PathVariable("head") long head) {
		pendingADUpdateService.deleteOperationsByIdLessThan(head + 1);
		
		return new ResponseEntity<>(HttpStatus.OK);
	}

	@GetMapping("/api/ad/v2/sync")
	public ResponseEntity<ADSyncResult> getPendingUpdates() {
		ADSyncResult result = new ADSyncResult();
		result.setAssignments(new ArrayList<ADGroupAssignments>());

		// compute sets of userIds and itSystemIds that are dirty, filtered for duplicates
		List<DirtyADGroup> updates = pendingADUpdateService.find100();

		// compute max
		long maxId = 0;
		try {
			Long maxIdCandidate = updates.stream().map(u -> u.getId()).max(Comparator.comparing(Long::valueOf)).get();
			maxId = maxIdCandidate;
		}
		catch (NoSuchElementException ex) {
			; // can be safely ignored
		}
		
		// filter duplicates (by identifier)
		updates = updates.stream().filter(distinctByKey(s -> s.getIdentifier())).collect(Collectors.toList());

		for (DirtyADGroup update : updates) {
			String groupName = update.getIdentifier();
			SystemRole systemRole = systemRoleService.getFirstByIdentifierAndItSystemId(groupName, update.getItSystemId());
			if (systemRole == null) {
				log.warn("Could not find SystemRole '" + groupName + "' in itSystem " + update.getItSystemId());
				continue;
			}

			// get all users that has a given system-role
			Set<String> sAMAccountNames = new HashSet<>();
			List<UserRole> userRoles = systemRoleService.userRolesWithSystemRole(systemRole);
			for (UserRole userRole : userRoles) {
				List<UserWithRole> users = userService.getUsersWithUserRole(userRole, true);

				if (users != null && users.size() > 0) {
					for (UserWithRole user : users) {
						sAMAccountNames.add(user.getUser().getUserId());
					}
				}
			}

			ADGroupAssignments assignment = new ADGroupAssignments();
			assignment.setGroupName(groupName);
			assignment.setSAMAccountNames(new ArrayList<>(sAMAccountNames));

			result.getAssignments().add(assignment);
		}

		result.setHead(maxId);
		result.setMaxHead(pendingADUpdateService.findMaxHead());
		
		return new ResponseEntity<>(result, HttpStatus.OK);
	}
	
	@DeleteMapping("/api/ad/v2/sync/{head}")
	public ResponseEntity<String> flagSyncPerformed(@PathVariable("head") long head, @RequestParam(name = "maxHead", required = false, defaultValue = "0") long maxHead) {
		pendingADUpdateService.deleteByIdLessThan(head + 1, maxHead);
		
		return new ResponseEntity<>(HttpStatus.OK);
	}
	
	private static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
		Map<Object, Boolean> seen = new ConcurrentHashMap<>();

		return t -> seen.putIfAbsent(keyExtractor.apply(t), Boolean.TRUE) == null;
	}
}
