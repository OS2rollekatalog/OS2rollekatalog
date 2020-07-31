package dk.digitalidentity.rc.dao;

import java.util.List;

import org.springframework.data.repository.CrudRepository;

import dk.digitalidentity.rc.dao.model.PendingManualUpdate;

public interface PendingManualUpdateDao extends CrudRepository<PendingManualUpdate, Long> {

	public List<PendingManualUpdate> findAll();
	
	public List<PendingManualUpdate> findByUserId(String userId);
	
	public List<PendingManualUpdate> findByItSystemId(Long itSystemId);

	public void deleteByIdLessThan(long head);

	public List<PendingManualUpdate> findFirst100ByOrderByIdAsc();
}
