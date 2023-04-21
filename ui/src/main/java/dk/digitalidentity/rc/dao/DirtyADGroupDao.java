package dk.digitalidentity.rc.dao;

import java.util.List;
import java.util.Set;

import dk.digitalidentity.rc.dao.model.Domain;
import org.springframework.data.repository.CrudRepository;

import dk.digitalidentity.rc.dao.model.DirtyADGroup;

public interface DirtyADGroupDao extends CrudRepository<DirtyADGroup, Long> {

	public List<DirtyADGroup> findAll();

	public List<DirtyADGroup> findFirst100ByDomainOrderByIdAsc(Domain domain);

	public List<DirtyADGroup> findByIdLessThanAndDomain(long head, Domain domain);

	public void deleteByItSystemId(long id);

	public void deleteByIdentifierIn(Set<String> toDeleteIdentifiers);
	public void deleteByIdentifierInAndIdLessThan(Set<String> toDeleteIdentifiers, long maxHead);

	public DirtyADGroup findTopByOrderByIdDesc();
}
