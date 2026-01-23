package dk.digitalidentity.rc.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import dk.digitalidentity.rc.config.Constants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dk.digitalidentity.rc.dao.SystemRoleDao;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.SystemRole;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;

@RequiredArgsConstructor
@Service
public class SystemRoleService {
	private final SystemRoleDao systemRoleDao;
	private final UserRoleService userRoleService;
	private final ItSystemService itSystemService;

	public SystemRole getById(long id) {
		return systemRoleDao.findById(id);
	}

	public Optional<SystemRole> getOptionalById(long id) {
		return Optional.ofNullable(systemRoleDao.findById(id));
	}

	public List<SystemRole> getByItSystem(ItSystem itSystem) {
		return systemRoleDao.findByItSystem(itSystem);
	}

	@Transactional
	public List<SystemRole> getByItSystem(ItSystem itSystem, Consumer<SystemRole> consumer) {
		List<SystemRole> systemRoles = systemRoleDao.findByItSystem(itSystem);

		if (consumer != null) {
			systemRoles.forEach(consumer);
		}

		return systemRoles;
	}

	public SystemRole getByUuid(String uuid) {
		return systemRoleDao.findByUuid(uuid);
	}

	public List<SystemRole> findByItSystemAndUuidNotNull(ItSystem itSystem) {
		return systemRoleDao.findByItSystemAndUuidNotNull(itSystem);
	}

	public SystemRole getFirstByIdentifierAndItSystemId(String identifier, long itSystemId) {
		List<SystemRole> result = systemRoleDao.findByIdentifierAndItSystemId(identifier, itSystemId);
		if (result != null && !result.isEmpty()) {
			return result.getFirst();
		}

		return null;
	}

	@Transactional
	public SystemRole save(SystemRole systemRole) {
		return systemRoleDao.save(systemRole);
	}

	@Transactional
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
		candidates.removeIf(ur -> ur.getSystemRoleAssignments().stream()
				.noneMatch(sysRoleAssignment -> systemRole.getId() == sysRoleAssignment.getSystemRole().getId()));

		return candidates;

	}

	public boolean isInUse(SystemRole systemRole) {
		return userRoleService.countBySystemRoleAssignmentsSystemRole(systemRole) > 0;
	}

	public boolean belongsToItSystemWithDifferentWeight(SystemRole systemRole) {
		if (systemRole == null) {
			return false;
		}

		Set<Integer> weights = new HashSet<>();
		for (SystemRole sr : getByItSystem(systemRole.getItSystem())) {
			weights.add(sr.getWeight());
		}

		return weights.size() > 1;
	}

	@Transactional
	public List<SystemRole> getByItSystemSystemType(ItSystemType systemType, Consumer<SystemRole> consumer) {
		List<SystemRole> result = systemRoleDao.findByItSystemSystemType(systemType);

		if (consumer != null) {
			result.forEach(consumer);
		}

		return result;
	}

	@Transactional
	public void saveAll(List<SystemRole> systemRoles) {
		systemRoleDao.saveAll(systemRoles);
	}

	@Transactional
	public void deleteAll(List<SystemRole> systemRoles) {
		systemRoleDao.deleteAll(systemRoles);
	}

	@Transactional
	public SystemRole createForRoleCatalogue(String name,  String identifier, String description) {
		ItSystem itSystem = itSystemService.getFirstByIdentifier(Constants.ROLE_CATALOGUE_IDENTIFIER);
		SystemRole systemRole = new SystemRole();
		systemRole.setItSystem(itSystem);
		systemRole.setName(name);
		systemRole.setDescription(description);
		systemRole.setIdentifier(identifier);
		systemRole.setSupportedConstraintTypes(new ArrayList<>());
		return systemRoleDao.save(systemRole);
	}
}
