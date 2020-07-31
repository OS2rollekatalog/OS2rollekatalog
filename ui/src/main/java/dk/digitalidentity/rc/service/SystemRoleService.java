package dk.digitalidentity.rc.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dk.digitalidentity.rc.dao.SystemRoleDao;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.SystemRole;
import dk.digitalidentity.rc.dao.model.UserRole;

@Service
public class SystemRoleService {

	@Autowired
	private SystemRoleDao systemRoleDao;
	
	@Autowired
	private UserRoleService userRoleService;

	public SystemRole getById(long id) {
		return systemRoleDao.getById(id);
	}

	public List<SystemRole> getByItSystem(ItSystem itSystem) {
		return systemRoleDao.findByItSystem(itSystem);
	}

	public SystemRole getByUuid(String uuid) {
		return systemRoleDao.getByUuid(uuid);
	}

	public List<SystemRole> findByItSystemAndUuidNotNull(ItSystem itSystem) {
		return systemRoleDao.findByItSystemAndUuidNotNull(itSystem);
	}

	public SystemRole getFirstByIdentifierAndItSystemId(String identifier, long itSystemId) {
		List<SystemRole> result = systemRoleDao.findByIdentifierAndItSystemId(identifier, itSystemId);
		if (result != null && result.size() > 0) {
			return result.get(0);
		}

		return null;
	}

	public SystemRole save(SystemRole systemRole) {
		return systemRoleDao.save(systemRole);
	}

	public void delete(SystemRole systemRole) {
		systemRoleDao.delete(systemRole);
	}

	public List<SystemRole> findByItSystem(ItSystem itSystem) {
		return systemRoleDao.findByItSystem(itSystem);
	}

	// not used by actual code - only for testing purposes
	public Iterable<SystemRole> save(List<SystemRole> systemRoles) {
		return systemRoleDao.saveAll(systemRoles);
	}
	
	public List<UserRole> userRolesWithSystemRole(SystemRole systemRole) {
		
		// find all potential candidates
		List<UserRole> candidates = userRoleService.getByItSystem(systemRole.getItSystem());

		// filter
		candidates.removeIf(ur -> !ur.getSystemRoleAssignments().stream().anyMatch(sysRoleAssignment -> systemRole.getId() == sysRoleAssignment.getSystemRole().getId()));
		
		return candidates;

	}
	
	public boolean isInUse(SystemRole systemRole) {
		if (userRoleService.countBySystemRoleAssignmentsSystemRole(systemRole) > 0) {
			return true;
		}

		return false;
	}
}
