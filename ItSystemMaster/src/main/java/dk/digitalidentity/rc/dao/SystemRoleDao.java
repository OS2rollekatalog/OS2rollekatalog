package dk.digitalidentity.rc.dao;

import java.util.List;

import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.SystemRole;
import org.springframework.data.repository.CrudRepository;

public interface SystemRoleDao extends CrudRepository<SystemRole, Long> {
	SystemRole findById(long id);

	List<SystemRole> findAll();

	List<SystemRole> findByItSystem(ItSystem itSystem);
}
