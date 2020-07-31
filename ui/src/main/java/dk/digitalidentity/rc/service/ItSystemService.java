package dk.digitalidentity.rc.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dk.digitalidentity.rc.dao.ItSystemDao;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;

@Service
public class ItSystemService {

	@Autowired
	private ItSystemDao itSystemDao;

	@Autowired
	private UserRoleService userRoleService;

	@Autowired
	private UserService userService;

	@Autowired
	private OrgUnitService orgUnitService;

	@Autowired
	private PositionService positionService;

	public List<ItSystem> getAll() {
		List<ItSystem> result = itSystemDao.findAll();

		return result.stream().sorted((o1, o2) -> o1.getName().toLowerCase().compareTo(o2.getName().toLowerCase())).collect(Collectors.toList());
	}

	public ItSystem getFirstByIdentifier(String identifier) {
		List<ItSystem> result = itSystemDao.findByIdentifier(identifier);
		if (result != null && result.size() > 0) {
			return result.get(0);
		}

		return null;
	}

	public ItSystem getFirstByName(String name) {
		List<ItSystem> result = itSystemDao.findByIdentifier(name);
		if (result != null && result.size() > 0) {
			return result.get(0);
		}

		return null;
	}

	public ItSystem save(ItSystem itSystem) {
		return itSystemDao.save(itSystem);
	}

	public ItSystem getById(long id) {
		return itSystemDao.getById(id);
	}

	public ItSystem getByUuid(String uuid) {
		return itSystemDao.getByUuid(uuid);
	}

	public List<ItSystem> getBySystemType(ItSystemType systemType) {
		return itSystemDao.findBySystemType(systemType);
	}

	public List<ItSystem> getBySubscribedToNotNull() {
		return itSystemDao.findBySubscribedToNotNull();
	}

	public long count() {
		return itSystemDao.count();
	}

	public List<ItSystem> findByIdentifier(String identifier) {
		return itSystemDao.findByIdentifier(identifier);
	}

	public void delete(ItSystem itSystem) {
		itSystemDao.delete(itSystem);
	}

	public int getUnusedUserRolesCount(ItSystem itSystem) {
		int sum = 0;
		
		List<UserRole> userRoles = userRoleService.getByItSystem(itSystem);
		for (UserRole userRole : userRoles) {
			if (userService.countAllWithRole(userRole) > 0) {
				continue;
			}
			
			if (orgUnitService.countAllWithRole(userRole) > 0) {
				continue;
			}
			
			if (positionService.getAllWithRole(userRole).size() > 0) {
				continue;
			}

			sum++;
		}
		
		return sum;
	}
	
	public List<ItSystem> getVisible() {
		List<ItSystem> result = itSystemDao.findByHiddenFalse();
		
		return result.stream().sorted((o1, o2) -> o1.getName().toLowerCase().compareTo(o2.getName().toLowerCase())).collect(Collectors.toList());
	}
}
