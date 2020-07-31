package dk.digitalidentity.rc.dao;

import java.util.List;

import dk.digitalidentity.rc.dao.model.ItSystemMaster;
import org.springframework.data.repository.CrudRepository;

public interface ItSystemMasterDao extends CrudRepository<ItSystemMaster, Long> {
	List<ItSystemMaster> findAll();
	ItSystemMaster findByMasterId(String id);
}
