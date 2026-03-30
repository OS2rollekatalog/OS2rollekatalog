package dk.digitalidentity.rc.service;

import dk.digitalidentity.rc.dao.DirtyADGroupDao;
import dk.digitalidentity.rc.dao.PendingADGroupOperationDao;
import dk.digitalidentity.rc.dao.SystemRoleDao;
import dk.digitalidentity.rc.dao.model.ADConfiguration;
import dk.digitalidentity.rc.dao.model.Client;
import dk.digitalidentity.rc.dao.model.DirtyADGroup;
import dk.digitalidentity.rc.dao.model.Domain;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.PendingADGroupOperation;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.SystemRole;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.assignment.CurrentAssignment;
import dk.digitalidentity.rc.dao.model.enums.ADGroupType;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import dk.digitalidentity.rc.service.assignment.AssignmentService;
import dk.digitalidentity.rc.service.util.FilterMatcher;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PendingADUpdateService {

	@Autowired
	private OrgUnitService orgUnitService;

	@Autowired
	private ItSystemService itSystemService;

	@Autowired
	private SystemRoleService systemRoleService;

	@Autowired
	private DirtyADGroupDao dirtyADGroupDao;

	@Autowired
	private SystemRoleDao systemRoleDao;

	@Autowired
	private PendingADGroupOperationDao pendingADGroupOperationDao;

	@Autowired
	private ADConfigurationService adConfigurationService;

	@Autowired
	private ClientService clientService;

	@Autowired
	private AssignmentService assignmentService;

	public PendingADGroupOperation save(PendingADGroupOperation operation) {
		return pendingADGroupOperationDao.save(operation);
	}

	public void addSystemRole(final SystemRole systemRole, final ADGroupType adGroupType, final boolean universal) {
		if (systemRole.getItSystem().getSystemType().equals(ItSystemType.AD) && !adGroupType.equals(ADGroupType.NONE)) {
			PendingADGroupOperation operation = new PendingADGroupOperation();
			operation.setActive(true);
			operation.setItSystemIdentifier(systemRole.getItSystem().getIdentifier());
			operation.setSystemRoleId(systemRole.getId());
			operation.setSystemRoleIdentifier(systemRole.getIdentifier());
			operation.setTimestamp(new Date());
			operation.setAdGroupType(adGroupType);
			operation.setUniversal(universal);
			operation.setDomain(systemRole.getItSystem().getDomain());

			save(operation);
		}
	}

	// we always add to queue, duplicates are dealt with elsewhere
	public void addItSystemToQueue(ItSystem itSystem) {
		if (!itSystem.getSystemType().equals(ItSystemType.AD) || itSystem.isPaused() || itSystem.isReadonly()) {
			return;
		}

		List<SystemRole> systemRoles = systemRoleDao.findByItSystem(itSystem);
		for (SystemRole systemRole : systemRoles) {
			DirtyADGroup dirty = new DirtyADGroup();
			dirty.setIdentifier(systemRole.getIdentifier());
			dirty.setItSystemId(itSystem.getId());
			dirty.setTimestamp(new Date());
			dirty.setDomain(itSystem.getDomain());

			dirtyADGroupDao.save(dirty);
		}
	}

	@Transactional
	public void removeItSystemFromQueue(ItSystem itSystem) {
		dirtyADGroupDao.deleteByItSystemId(itSystem.getId());
	}

	public void addUserRoleToQueue(UserRole userRole) {
		ItSystem itSystem = userRole.getItSystem();

		if (isITSystemIneligible(itSystem)) {
			return;
		}

		List<SystemRole> systemRoles = userRole.getSystemRoleAssignments().stream().map(sra -> sra.getSystemRole()).collect(Collectors.toList());
		for (SystemRole systemRole : systemRoles) {
			DirtyADGroup dirty = new DirtyADGroup();
			dirty.setIdentifier(systemRole.getIdentifier());
			dirty.setItSystemId(itSystem.getId());
			dirty.setTimestamp(new Date());
			dirty.setDomain(itSystem.getDomain());

			dirtyADGroupDao.save(dirty);
		}
	}

	public void addRoleGroupToQueue(RoleGroup roleGroup) {
		if (roleGroup != null && roleGroup.getUserRoleAssignments() != null) {
			List<UserRole> userRoles = roleGroup.getUserRoleAssignments().stream().map(ura -> ura.getUserRole()).collect(Collectors.toList());

			for (UserRole userRole : userRoles) {
				addUserRoleToQueue(userRole);
			}
		}
	}

	// not pretty - and also not perfect (what happens if a user's OU is moved so it is a child of another OU, and that new parent OU has
	// inherit rules - nothing is what happens, because it is not an event we can listen for.... but next time anyone messes with the ad groups,
	// we will perform the update, so eventual consistency is the way home here
	public void addUserToQueue(User user, Position position) {

		// check rolegroups assigned to OU that the position points to
		List<RoleGroup> rgs = orgUnitService.getRoleGroups(position.getOrgUnit(), true);

		for (RoleGroup roleGroup : rgs) {
			if (roleGroup.getUserRoleAssignments() != null) {
				List<UserRole> userRoles = roleGroup.getUserRoleAssignments().stream().map(ura -> ura.getUserRole()).collect(Collectors.toList());

				for (UserRole userRole : userRoles) {
					if (userRole.getItSystem().getSystemType().equals(ItSystemType.AD) && !userRole.getItSystem().isPaused()) {
						addUserRoleToQueue(userRole);
					}
				}
			}
		}

		// check userroles assigned to the OU that the position points to
		List<UserRole> urs = orgUnitService.getUserRoles(position.getOrgUnit(), true);

		for (UserRole userRole : urs) {
			if (userRole.getItSystem().getSystemType().equals(ItSystemType.AD) && !userRole.getItSystem().isPaused()) {
				addUserRoleToQueue(userRole);
			}
		}
	}

	@Transactional
	public void deleteByIdLessThan(long head, long maxHead, Domain domain) {
		List<DirtyADGroup> toDelete = dirtyADGroupDao.findByIdLessThanAndDomain(head, domain);
		Set<String> toDeleteIdentifiers = toDelete.stream().map(d -> d.getIdentifier()).collect(Collectors.toSet());

		if (maxHead > 0) {
			dirtyADGroupDao.deleteByIdentifierInAndIdLessThan(toDeleteIdentifiers, maxHead + 1);
		}
		else {
			dirtyADGroupDao.deleteByIdentifierIn(toDeleteIdentifiers);
		}
	}

	@Transactional
	public void deleteOperationsByIdLessThan(long head, Domain foundDomain) {
		pendingADGroupOperationDao.deleteByDomainAndIdLessThan(foundDomain, head);
	}

	public List<String> getAllGroupIdentifiers() {
		Set<String> managedGroups = new HashSet<>();
		for (ItSystem itSystem : itSystemService.getBySystemType(ItSystemType.AD)) {
			for (SystemRole systemRole : systemRoleService.findByItSystem(itSystem)) {
				managedGroups.add(systemRole.getIdentifier());
			}
		}

		return new ArrayList<>(managedGroups);
	}

	public List<DirtyADGroup> find100(Domain domain) {
		return dirtyADGroupDao.findFirst100ByDomainOrderByIdAsc(domain);
	}

	public List<PendingADGroupOperation> find100Operations(Domain domain) {
		return pendingADGroupOperationDao.findFirst100ByDomainOrderByIdAsc(domain);
	}

	public long findMaxHead() {
		DirtyADGroup group = dirtyADGroupDao.findTopByOrderByIdDesc();
		if (group != null) {
			return group.getId();
		}

		return 0;
	}

	@Transactional
	public void addAllGroups(final long domainId) {
		log.info("Adding all groups for domain {} to queue", domainId);
		// Filters IT systems by AD group and domain
		itSystemService.getAll().stream()
			.filter(its -> !isITSystemIneligible(its))
			.filter(its -> its.getDomain() != null && domainId == its.getDomain().getId())
			.forEach(this::addItSystemToQueue);
	}

	// we always add to queue, duplicates are dealt with elsewhere
	@Transactional
	public void addADGroupsFromMemberShipSyncFilter() {
		log.info("Starting addADGroupsFromMemberShipSyncFilter");
		int totalProcessed = 0;
		int totalMatched = 0;
		int totalSkipped = 0;

		for (Client adSyncService : clientService.findADSyncServices()) {
			ADConfiguration config = adConfigurationService.getByClient(adSyncService);
			if (config == null) {
				log.debug("No AD configuration found for client: {}", adSyncService.getName());
				continue;
			}

			List<String> filterMap = config.getJson().getMembershipSyncFeatureFilterMap();
			if (!config.getJson().isMembershipSyncFeatureEnabled() || filterMap == null || filterMap.isEmpty()) {
				log.debug("No membership sync filters configured for client: {}", adSyncService.getName());
				continue;
			}

			log.info("Processing client '{}' with {} filter(s)", adSyncService.getName(), filterMap.size());

			for (ItSystem itSystem : itSystemService.getBySystemType(ItSystemType.AD)) {
				if (isITSystemIneligible(itSystem)) {
					continue;
				}

				List<SystemRole> systemRoles = systemRoleDao.findByItSystem(itSystem);
				log.debug("Found {} SystemRoles for IT system '{}'", systemRoles.size(), itSystem.getName());

				for (SystemRole systemRole : systemRoles) {
					totalProcessed++;

					try {
						// Check if SystemRole matches any filter
						if (FilterMatcher.systemRoleMatchesGroupFilter(systemRole, filterMap)) {
							log.debug("SystemRole '{}' matches filter - adding to dirty AD groups", systemRole.getName());

							DirtyADGroup dirty = new DirtyADGroup();
							dirty.setIdentifier(systemRole.getIdentifier());
							dirty.setItSystemId(itSystem.getId());
							dirty.setTimestamp(new Date());
							dirty.setDomain(itSystem.getDomain());

							dirtyADGroupDao.save(dirty);
							totalMatched++;

							log.debug("Added SystemRole '{}' to dirty AD groups", systemRole.getName());
						} else {
							totalSkipped++;
							log.debug("SystemRole '{}' does not match any filter - skipping",
									systemRole.getName());
						}
					} catch (Exception e) {
						log.error("Error processing SystemRole '{}' (ID: {}): {}",
								systemRole.getName(),
								systemRole.getId(),
								e.getMessage(),
								e);
					}
				}
			}
		}

		log.info("Completed addADGroupsFromMemberShipSyncFilter - Processed: {}, Matched: {}, Skipped: {}",
				totalProcessed, totalMatched, totalSkipped);
	}

	public void addADGroupsWithStartDateToday() {
		log.info("Starting addADGroupsFromMemberShipSyncFilter");
		int totalProcessed = 0;

		for (ItSystem itSystem : itSystemService.getBySystemType(ItSystemType.AD)) {
			if (itSystem.isPaused()) {
				log.debug("Skipping paused IT system: {}", itSystem.getName());
				continue;
			}

			if (itSystem.isReadonly()) {
				log.debug("Skipping readonly IT system: {}", itSystem.getName());
				continue;
			}

			Set<CurrentAssignment> assignmentsWithStartToday = assignmentService.findByStartDateTodayAndItSystem(itSystem);
			log.debug("Found {} assignment with startDate today for IT system '{}'", assignmentsWithStartToday.size(), itSystem.getName());

			for (CurrentAssignment currentAssignment : assignmentsWithStartToday) {
				UserRole userRole = currentAssignment.getUserRole();
				totalProcessed++;
				addUserRoleToQueue(userRole);
				log.debug("Added systemRoles from userRole '{}' to dirty AD groups", userRole.getName());
			}
		}

		log.info("Completed addADGroupsFromMemberShipSyncFilter - Processed: {}",
			totalProcessed);
	}

	/**
	 * Skips paused or readonly IT systems
	 */
	private static boolean isITSystemIneligible(final ItSystem itSystem) {
		return (!itSystem.getSystemType().equals(ItSystemType.AD) || itSystem.isPaused() || itSystem.isReadonly());
	}
}
