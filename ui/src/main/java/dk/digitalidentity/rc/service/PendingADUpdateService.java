package dk.digitalidentity.rc.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.transaction.Transactional;

import dk.digitalidentity.rc.dao.model.Domain;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dk.digitalidentity.rc.dao.DirtyADGroupDao;
import dk.digitalidentity.rc.dao.PendingADGroupOperationDao;
import dk.digitalidentity.rc.dao.SystemRoleDao;
import dk.digitalidentity.rc.dao.model.DirtyADGroup;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.PendingADGroupOperation;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.SystemRole;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;

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
	
	public PendingADGroupOperation save(PendingADGroupOperation operation) {
		return pendingADGroupOperationDao.save(operation);
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
		
		if (!itSystem.getSystemType().equals(ItSystemType.AD) || itSystem.isPaused() || itSystem.isReadonly()) {
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

		// check rolegroups assigned to position
		if (position.getRoleGroupAssignments() != null) {
	      	List<RoleGroup> prg = position.getRoleGroupAssignments().stream().map(ura -> ura.getRoleGroup()).collect(Collectors.toList());

			for (RoleGroup roleGroup : prg) {
				List<UserRole> userRoles = roleGroup.getUserRoleAssignments().stream().map(ura -> ura.getUserRole()).collect(Collectors.toList());

				for (UserRole userRole : userRoles) {
					if (userRole.getItSystem().getSystemType().equals(ItSystemType.AD) && !userRole.getItSystem().isPaused()) {
						addUserRoleToQueue(userRole);
					}
				}
			}
		}
		
		// check userroles assigned to position
		if (position.getUserRoleAssignments() != null) {
			List<UserRole> userRoles = position.getUserRoleAssignments().stream().map(ura -> ura.getUserRole()).collect(Collectors.toList());

			for (UserRole userRole : userRoles) {
				if (userRole.getItSystem().getSystemType().equals(ItSystemType.AD) && !userRole.getItSystem().isPaused()) {
					addUserRoleToQueue(userRole);
				}
			}
		}
		
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
}
