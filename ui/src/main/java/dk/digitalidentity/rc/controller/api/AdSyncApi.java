package dk.digitalidentity.rc.controller.api;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
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
import dk.digitalidentity.rc.dao.model.Domain;
import dk.digitalidentity.rc.dao.model.SystemRole;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.security.RequireApiReadAccessRole;
import dk.digitalidentity.rc.service.DomainService;
import dk.digitalidentity.rc.service.PendingADUpdateService;
import dk.digitalidentity.rc.service.SystemRoleService;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.rc.service.model.UserWithRole;
import dk.digitalidentity.rc.util.StreamExtensions;
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

	@Autowired
	private DomainService domainService;

	@GetMapping("/api/ad/v2/operations")
	public ResponseEntity<ADOperationsResult> getPendingOperations(@RequestParam(name = "domain", required = false) String domain) {
		Domain foundDomain = domainService.getDomainOrPrimary(domain);
		if (foundDomain == null) {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}

		ADOperationsResult result = new ADOperationsResult();
		
		result.setOperations(pendingADUpdateService.find100Operations(foundDomain));

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
	public ResponseEntity<String> flagOperationsPerformed(@PathVariable("head") long head, @RequestParam(name = "domain", required = false) String domain) {
		Domain foundDomain = domainService.getDomainOrPrimary(domain);
		if (foundDomain == null) {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}

		pendingADUpdateService.deleteOperationsByIdLessThan(head + 1, foundDomain);
		
		return new ResponseEntity<>(HttpStatus.OK);
	}

	@GetMapping("/api/ad/v2/sync")
	public ResponseEntity<ADSyncResult> getPendingUpdates(@RequestParam(name = "domain", required = false) String domain) {
		Domain foundDomain = domainService.getDomainOrPrimary(domain);
		if (foundDomain == null) {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}

		ADSyncResult result = new ADSyncResult();
		result.setAssignments(new ArrayList<ADGroupAssignments>());

		// compute sets of userIds and itSystemIds that are dirty, filtered for duplicates
		List<DirtyADGroup> updates = pendingADUpdateService.find100(foundDomain);
		
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
		updates = updates.stream().filter(StreamExtensions.distinctByKey(s -> s.getIdentifier())).collect(Collectors.toList());
		Map<String, SystemRole> dirtySystemRoles = new HashMap<>();
		
		// we want to add all systemRoles from itSystems that has weighted systemRoles if just one of them is dirty
		for (DirtyADGroup update : updates) {
			String groupName = update.getIdentifier();
			SystemRole systemRole = systemRoleService.getFirstByIdentifierAndItSystemId(groupName, update.getItSystemId());
			if (systemRole == null) {
				log.warn("Could not find SystemRole '" + groupName + "' in itSystem " + update.getItSystemId());
				continue;
			}
			
			dirtySystemRoles.put(groupName, systemRole);
			
			boolean differentWeightedItSystem = systemRoleService.belongsToItSystemWithDifferentWeight(systemRole);
			if (differentWeightedItSystem) {
				for (SystemRole sr : systemRoleService.getByItSystem(systemRole.getItSystem())) {
					dirtySystemRoles.put(sr.getIdentifier(), sr);
				}
			}
		}
		
		for (SystemRole dirtySystemRole : dirtySystemRoles.values()) {
			// get all users that has a given system-role
			Set<String> sAMAccountNames = new HashSet<>();
			List<UserRole> userRoles = systemRoleService.userRolesWithSystemRole(dirtySystemRole);
			for (UserRole userRole : userRoles) {
				List<UserWithRole> users = userService.getUsersWithUserRole(userRole, true);
				if (users != null && users.size() > 0) {
					for (UserWithRole user : users) {
						if (user.getUser().getDomain().getId() == foundDomain.getId()) {
							sAMAccountNames.add(user.getUser().getUserId());
						}
					}
				}
			}

			ADGroupAssignments assignment = new ADGroupAssignments();
			assignment.setItSystemId(dirtySystemRole.getItSystem().getId());
			assignment.setWeight(dirtySystemRole.getWeight());
			assignment.setGroupName(dirtySystemRole.getIdentifier());
			assignment.setSAMAccountNames(new ArrayList<>(sAMAccountNames));

			result.getAssignments().add(assignment);
		}

		// support weighted it-systems by removing assignments that should not have been added due to an assignment with higher weight
		// get distinct it systems
		var itSystems = result.getAssignments().stream().map( a -> a.getItSystemId()).distinct().collect(Collectors.toList());
		for( var itSystemId : itSystems ) {
			// get assignments belonging to this it system
			var itSystemAssignments = result.getAssignments().stream().filter(a -> a.getItSystemId() == itSystemId).collect(Collectors.toList());
			// get the distinct weights for this it system ordered by descending weight
			var weights = itSystemAssignments.stream().map(a -> a.getWeight()).sorted(Comparator.reverseOrder()).collect(Collectors.toList());
			// we don't want to remove any assignments that has highest weight, so remove this item from weight list
			// there is always minimum 1 weight. For unweighted it-systems there will be exactly 1 weight
			weights.remove(0);
			for( var weight : weights ) {
				for( var assignment : itSystemAssignments.stream().filter(a -> a.getWeight() == weight).collect(Collectors.toList()) ) {
					// remove any accountnames that are in an assignment with a higher weight
					assignment.getSAMAccountNames().removeIf(account -> itSystemAssignments.stream().anyMatch(a -> a.getWeight() > weight && a.getSAMAccountNames().contains(account)));
				}
			}
		}

		result.setHead(maxId);
		result.setMaxHead(pendingADUpdateService.findMaxHead());
		
		return new ResponseEntity<>(result, HttpStatus.OK);
	}

	@DeleteMapping("/api/ad/v2/sync/{head}")
	public ResponseEntity<String> flagSyncPerformed(@PathVariable("head") long head, @RequestParam(name = "maxHead", required = false, defaultValue = "0") long maxHead, @RequestParam(name = "domain", required = false) String domain) {
		Domain foundDomain = domainService.getDomainOrPrimary(domain);
		if (foundDomain == null) {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}
		pendingADUpdateService.deleteByIdLessThan(head + 1, maxHead, foundDomain);
		
		return new ResponseEntity<>(HttpStatus.OK);
	}	
}
