package dk.digitalidentity.rc.dao;

import java.util.List;

import dk.digitalidentity.rc.dao.model.ItSystem;
import org.springframework.data.repository.CrudRepository;

public interface ItSystemDao extends CrudRepository<ItSystem, Long> {
	List<ItSystem> findAll();
	List<ItSystem> findByName(String name);
	ItSystem findByMasterId(String id);
}
