package dk.digitalidentity.rc.service;

import java.util.List;

import dk.digitalidentity.rc.dao.SystemRoleDao;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.SystemRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SystemRoleService {

	@Autowired
	private SystemRoleDao systemRoleDao;

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
}
