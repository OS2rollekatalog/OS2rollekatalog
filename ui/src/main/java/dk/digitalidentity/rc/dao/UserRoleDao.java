package dk.digitalidentity.rc.dao;

import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.SystemRole;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.projections.UserRoleNameAndItSystemName;
import dk.digitalidentity.rc.dao.projections.UserRoleWeight;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface UserRoleDao extends CrudRepository<UserRole, Long> {
	@Query("SELECT ur FROM UserRole ur JOIN FETCH ur.itSystem WHERE ur.id = :id")
	Optional<UserRole> findByIdWithItSystem(@Param("id") Long id);

	@Query("""
	  SELECT new dk.digitalidentity.rc.dao.projections.UserRoleWeight(sra.userRole.id, MAX(sra.systemRole.weight))
	  FROM SystemRoleAssignment sra
	  WHERE sra.userRole.itSystem.id IN :itSystemIds
	  GROUP BY sra.userRole.id
	""")
	List<UserRoleWeight> findHeaviestSystemRoleWeights(@Param("itSystemIds") Collection<Long> itSystemIds);

	UserRole getByNameAndItSystem(String name, ItSystem itSystem);
	List<UserRole> findAll();
	UserRole getByIdentifier(String identifier);
	List<UserRole> findByItSystem(ItSystem itSystem);
	<S extends UserRole> S save(S entity);
	UserRole getByItSystemAndIdentifier(ItSystem itSystem, String identifier);
	int countBySystemRoleAssignmentsSystemRole(SystemRole systemRole);
	List<UserRole> findBySensitiveRoleTrue();
	List<UserRole> findByLinkedSystemRoleNotNull();


	// for production
	List<UserRole> getByDelegatedFromCvrNotNullAndItSystemIdentifierNot(String itSystemIdentifier);

	// for test
	List<UserRole> getByItSystemAndDelegatedFromCvrNotNull(ItSystem itSystem);

	Set<UserRole> findBySystemRoleAssignments_SystemRole(SystemRole systemRole);

	Set<UserRole> findBySystemRoleAssignments_SystemRoleIn(Collection<SystemRole> systemRoles);

	Set<UserRole> findAllByIdIn(Collection<Long> ids);

	List<UserRole> findByItSystemIn(Collection<ItSystem> itSystems);

	<T> Optional<T> findById(Long id, Class<T> projectionType);

	@Query("""
		SELECT new dk.digitalidentity.rc.dao.projections.UserRoleNameAndItSystemName(ur.name, its.name)
		FROM UserRole ur LEFT JOIN ur.itSystem its
		WHERE ur.id = :id
		""")
	Optional<UserRoleNameAndItSystemName> findNameAndItSystemNameById(@Param("id") Long id);

}
