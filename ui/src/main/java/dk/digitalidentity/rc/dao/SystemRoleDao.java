package dk.digitalidentity.rc.dao;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.SystemRole;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;

public interface SystemRoleDao extends CrudRepository<SystemRole, Long> {
	SystemRole findById(long id);
	List<SystemRole> findAll();
	List<SystemRole> findByItSystem(ItSystem itSystem);
	List<SystemRole> findByItSystemAndUuidNotNull(ItSystem itSystem);
	SystemRole findByUuid(String uuid);
	List<SystemRole> findByIdentifierAndItSystemId(String identifier, long itSystemId);
	List<SystemRole> findByItSystemSystemType(ItSystemType systemType);
	List<SystemRole> findByMaximumAssignmentsNotNull();
	List<SystemRole> findByIdIn(Collection<Long> ids);

	@Query(value = """
		SELECT DISTINCT sr.*
		FROM current_assignment ca
		JOIN system_role_assignments sra ON sra.user_role_id = ca.assignment_user_role_id
		JOIN system_roles sr ON sr.id = sra.system_role_id
		WHERE ca.assignment_user_uuid = :userUuid
		""", nativeQuery = true)
	List<SystemRole> findDistinctByUserUuid(@Param("userUuid") String userUuid);

	@Query(value = """
		SELECT DISTINCT sr.*
		FROM current_assignment ca
		JOIN system_role_assignments sra ON sra.user_role_id = ca.assignment_user_role_id
		JOIN system_roles sr ON sr.id = sra.system_role_id
		WHERE ca.assignment_user_uuid = :userUuid
		  AND ca.assignment_it_system_id IN :itSystemIds
		""", nativeQuery = true)
	List<SystemRole> findDistinctByUserUuidAndItSystemIdIn(@Param("userUuid") String userUuid, @Param("itSystemIds") Collection<Long> itSystemIds);
}
