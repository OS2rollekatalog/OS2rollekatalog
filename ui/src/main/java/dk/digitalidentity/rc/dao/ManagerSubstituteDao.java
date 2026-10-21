package dk.digitalidentity.rc.dao;

import java.util.Set;

import dk.digitalidentity.rc.dao.model.ManagerSubstitute;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

public interface ManagerSubstituteDao extends ListCrudRepository<ManagerSubstitute, Long> {

	@Query("SELECT ms.orgUnit.uuid FROM users_manager_substitute ms WHERE ms.substitute.uuid = :substituteUuid")
	Set<String> findOrgUnitUuidsBySubstituteUuid(@Param("substituteUuid") String substituteUuid);
}
