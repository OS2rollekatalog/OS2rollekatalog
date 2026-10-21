package dk.digitalidentity.rc.dao;

import java.util.List;
import java.util.Set;

import dk.digitalidentity.rc.dao.model.Domain;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

import dk.digitalidentity.rc.dao.model.DirtyADGroup;

public interface DirtyADGroupDao extends CrudRepository<DirtyADGroup, Long> {

	public List<DirtyADGroup> findAll();

	public List<DirtyADGroup> findFirst30ByDomainOrderByIdAsc(Domain domain);

	public List<DirtyADGroup> findByIdLessThanAndDomain(long head, Domain domain);

	public void deleteByItSystemId(long id);

	public void deleteByIdentifierIn(Set<String> toDeleteIdentifiers);
	public void deleteByIdentifierInAndIdLessThan(Set<String> toDeleteIdentifiers, long maxHead);

	public DirtyADGroup findTopByOrderByIdDesc();
	
	@Modifying
	@Query(nativeQuery = true, value = """
        DELETE FROM dirty_ad_groups
        WHERE id NOT IN (
            SELECT id FROM (SELECT MIN(id) AS id FROM dirty_ad_groups GROUP BY identifier, it_system_id, domain_id) keepers
        );
    """)
	public void cleanupDuplicates();

}
