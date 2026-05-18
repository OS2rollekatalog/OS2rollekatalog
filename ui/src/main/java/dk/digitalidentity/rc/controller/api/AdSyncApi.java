package dk.digitalidentity.rc.controller.api;

import dk.digitalidentity.rc.controller.api.dto.ADGroupAssignments;
import dk.digitalidentity.rc.controller.api.dto.ADOperationsResult;
import dk.digitalidentity.rc.controller.api.dto.ADSyncResult;
import dk.digitalidentity.rc.dao.model.DirtyADGroup;
import dk.digitalidentity.rc.dao.model.Domain;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.SystemRole;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.assignment.CurrentAssignment;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import dk.digitalidentity.rc.security.RequireApiReadAccessRole;
import dk.digitalidentity.rc.service.DomainService;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.PendingADUpdateService;
import dk.digitalidentity.rc.service.SystemRoleService;
import dk.digitalidentity.rc.service.assignment.AssignmentService;
import dk.digitalidentity.rc.util.StreamExtensions;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@RequireApiReadAccessRole
@Slf4j
@RestController
@SecurityRequirement(name = "ApiKey")
public class AdSyncApi {

	@Autowired
	private PendingADUpdateService pendingADUpdateService;

	@Autowired
	private SystemRoleService systemRoleService;

	@Autowired
	private DomainService domainService;

	@Autowired
	private ItSystemService itSystemService;

	@Autowired
	private AssignmentService assignmentService;

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

	@Transactional(readOnly = true)
	@GetMapping("/api/ad/v2/sync")
	public ResponseEntity<ADSyncResult> getPendingUpdates(@RequestParam(name = "domain", required = false) String domain, @RequestParam(name = "fullsync", required = false, defaultValue = "false") boolean fullSync) {
		Domain foundDomain = domainService.getDomainOrPrimary(domain);
		if (foundDomain == null) {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}

		ADSyncResult result = new ADSyncResult();
		result.setAssignments(new ArrayList<ADGroupAssignments>());

		List<DirtyADGroup> updates = new ArrayList<>();

		if (!fullSync) {
			// compute sets of userIds and itSystemIds that are dirty, filtered for duplicates
			updates = pendingADUpdateService.find100(foundDomain);

		} else {
			List<ItSystem> allADSystems = itSystemService.getBySystemType(ItSystemType.AD).stream()
					.filter(s -> s.getDomain() != null && foundDomain.getId() == s.getDomain().getId())
					.filter(s -> !s.isReadonly())
					.filter(s -> !s.isPaused())
					.toList();

			for (ItSystem itSystem : allADSystems) {
				List<SystemRole> systemRoles = systemRoleService.findByItSystem(itSystem);
				for (SystemRole systemRole : systemRoles) {
					DirtyADGroup dirtyADGroup = new DirtyADGroup();
					dirtyADGroup.setDomain(foundDomain);
					dirtyADGroup.setIdentifier(systemRole.getIdentifier());
					dirtyADGroup.setItSystemId(itSystem.getId());
					dirtyADGroup.setTimestamp(new Date());

					updates.add(dirtyADGroup);
				}
			}
		}

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

		// Build the full set of system roles to process. For weighted IT systems, all roles in the system
		// must be included even if only one is dirty — otherwise the user-removal step below cannot correctly
		// determine which users belong to a lower-weight group and should be removed from it.
		Set<Long> itSystemIds = updates.stream().map(d -> d.getItSystemId()).filter(id -> (id > 0)).collect(Collectors.toSet());

		Map<Long, List<SystemRole>> systemRoleMap = systemRoleService
				.getByItSystemIds(itSystemIds)
				.stream()
				.collect(Collectors.groupingBy(sr -> sr.getItSystem().getId()));

		for (DirtyADGroup update : updates) {
			List<SystemRole> siblings = systemRoleMap.get(update.getItSystemId());
			if (siblings == null) {
				log.error("Could not find it-system with id " + update.getItSystemId());
				continue;
			}

			SystemRole systemRole = siblings.stream().filter(sr -> Objects.equals(sr.getIdentifier(), update.getIdentifier())).findFirst().orElse(null);
			if (systemRole == null) {
				log.warn("Could not find SystemRole '" + update.getIdentifier() + "' in itSystem " + update.getItSystemId());
				continue;
			}

			dirtySystemRoles.put(update.getIdentifier(), systemRole);

			// If the IT system has roles with different weights (filterByWeight removes at least one),
			// pull in all sibling roles so the weight comparison below has the complete picture.
			if (systemRoleService.filterByWeight(siblings).size() < siblings.size()) {
				for (SystemRole sr : siblings) {
					dirtySystemRoles.put(sr.getIdentifier(), sr);
				}
			}
		}

		// these are almost always 1-1 anyway, but since it is possible to have 1-many, and we might only have a single SystemRole
		// that is dirty within a given UserRole, we do this filtering (though maybe it could be removed, and we just do a few extra
		// updates in those seldom scenarios).
		Map<SystemRole, Set<UserRole>> systemRoleToUserRoleMap = new HashMap<>();
		Set<UserRole> userRoles = systemRoleService.userRolesWithSystemRoles(dirtySystemRoles.values());

		for (UserRole userRole : userRoles) {
			userRole.getSystemRoleAssignments().forEach(sra -> {
				SystemRole systemRole = sra.getSystemRole();

				// only track systemRoles that are dirty (potentially the UserRole could contain a systemRole that was not dirty
				if (dirtySystemRoles.containsKey(systemRole.getIdentifier())) {
					Set<UserRole> userRoleResult = systemRoleToUserRoleMap.get(systemRole);
					if (userRoleResult == null) {
						userRoleResult = new HashSet<>();
						systemRoleToUserRoleMap.put(systemRole, userRoleResult);
					}
					
					userRoleResult.add(userRole);
				}
			});
		}

		Map<UserRole, List<CurrentAssignment>> assignmentMap = assignmentService.getActiveByUserRoles(userRoles)
				.stream()
				.collect(Collectors.groupingBy(CurrentAssignment::getUserRole));
		
		for (SystemRole dirtySystemRole : dirtySystemRoles.values()) {
			Set<UserRole> dirtyUserRoles = systemRoleToUserRoleMap.get(dirtySystemRole);
			if (dirtyUserRoles == null) {
				log.warn("Could not find any systemRoles matching systemRole " + dirtySystemRole.getId());
				continue;
			}

			// get all users that has a given system-role
			Set<String> sAMAccountNames = new HashSet<>();
			for (UserRole userRole : dirtyUserRoles) {
				List<CurrentAssignment> assignments = assignmentMap.get(userRole);
				if (assignments != null) {
					for (CurrentAssignment assignment : assignments) {
						if (assignment.getUser().getDomain().getId() == foundDomain.getId()) {
							sAMAccountNames.add(assignment.getUser().getUserId());
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
		List<Long> itSystems = result.getAssignments().stream().map( a -> a.getItSystemId()).distinct().collect(Collectors.toList());
		for (long itSystemId : itSystems) {
			// get assignments belonging to this it system
			List<ADGroupAssignments> itSystemAssignments = result.getAssignments().stream().filter(a -> a.getItSystemId() == itSystemId).collect(Collectors.toList());
			
			// get the distinct weights for this it system ordered by descending weight
			List<Integer> weights = itSystemAssignments.stream().map(a -> a.getWeight()).sorted(Comparator.reverseOrder()).collect(Collectors.toList());

			// we don't want to remove any assignments that has highest weight, so remove this item from weight list
			// there is always minimum 1 weight. For unweighted it-systems there will be exactly 1 weight
			weights.remove(0);
			for (int weight : weights) {
				for (ADGroupAssignments assignment : itSystemAssignments.stream().filter(a -> a.getWeight() == weight).collect(Collectors.toList()) ) {
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
