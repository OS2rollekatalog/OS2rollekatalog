package dk.digitalidentity.rc.dao;

import java.util.List;

import dk.digitalidentity.rc.dao.model.Domain;
import org.springframework.data.repository.CrudRepository;

import dk.digitalidentity.rc.dao.model.PendingADGroupOperation;

public interface PendingADGroupOperationDao extends CrudRepository<PendingADGroupOperation, Long> {

	public List<PendingADGroupOperation> findAll();

	public void deleteByDomainAndIdLessThan(Domain domain, long head);
	public List<PendingADGroupOperation> findFirst100ByDomainOrderByIdAsc(Domain domain);
}
