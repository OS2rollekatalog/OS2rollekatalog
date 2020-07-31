package dk.digitalidentity.rc.dao;

import java.util.List;

import org.springframework.data.repository.CrudRepository;

import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;

public interface ItSystemDao extends CrudRepository<ItSystem, Long> {
	List<ItSystem> findAll();
	List<ItSystem> findByIdentifier(String identifier);
	List<ItSystem> findByName(String name);
	List<ItSystem> findBySystemType(ItSystemType systemType);
	List<ItSystem> findBySubscribedToNotNull();
	ItSystem getByUuid(String uuid);
	ItSystem getById(long id);
	List<ItSystem> findByHiddenFalse();
}
