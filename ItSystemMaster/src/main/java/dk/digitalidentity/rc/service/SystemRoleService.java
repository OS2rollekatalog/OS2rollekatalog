package dk.digitalidentity.rc.service;

import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dk.digitalidentity.rc.dao.SystemRoleDao;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.SystemRole;

@Service
public class SystemRoleService {

	@Autowired
	private SystemRoleDao systemRoleDao;
	
	@Autowired
	private ItSystemService itSystemService;

	public SystemRole getById(long id) {
		return systemRoleDao.findById(id);
	}

	public List<SystemRole> getByItSystem(ItSystem itSystem) {
		return systemRoleDao.findByItSystem(itSystem);
	}

	public SystemRole save(SystemRole systemRole) {
		return systemRoleDao.save(systemRole);
	}

	public void delete(SystemRole systemRole) {
		systemRoleDao.delete(systemRole);
	}
	
	@Transactional
	public void delete(long id) {
		SystemRole systemRole = getById(id);
		if (systemRole == null) {
			return;
		}

		ItSystem itSystem = systemRole.getItSystem();
		itSystem.setLastModified(new Date());
		itSystemService.save(itSystem);

		systemRoleDao.deleteById(id);
	}
}
