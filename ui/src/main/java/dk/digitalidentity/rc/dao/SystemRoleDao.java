package dk.digitalidentity.rc.dao;

import java.util.List;

import org.springframework.data.repository.CrudRepository;

import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.SystemRole;

public interface SystemRoleDao extends CrudRepository<SystemRole, Long> {
	SystemRole getById(long id);
	List<SystemRole> findAll();
	List<SystemRole> findByItSystem(ItSystem itSystem);
	List<SystemRole> findByItSystemAndUuidNotNull(ItSystem itSystem);
	SystemRole findByUuid(String uuid);
	List<SystemRole> findByIdentifierAndItSystemId(String identifier, long itSystemId);
}
