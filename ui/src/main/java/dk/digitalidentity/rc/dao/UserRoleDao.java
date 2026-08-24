package dk.digitalidentity.rc.dao;

import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.SystemRole;
import dk.digitalidentity.rc.dao.model.UserRole;
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

	UserRole getByNameAndItSystem(String name, ItSystem itSystem);
	List<UserRole> findAll();
	UserRole getByIdentifier(String identifier);
	List<UserRole> findByItSystem(ItSystem itSystem);
	<S extends UserRole> S save(S entity);
	UserRole getByItSystemAndIdentifier(ItSystem itSystem, String identifier);
	int countBySystemRoleAssignmentsSystemRole(SystemRole systemRole);
	List<UserRole> findBySensitiveRoleTrue();
	List<UserRole> findByLinkedSystemRoleNotNull();

	// requester_permission/approver_permission er @Convert(List<enum> -> CSV),
	// så Hibernate 6 kan ikke binde en IN-parameter mod kolonnen. Vi bruger i stedet
	// MySQL/MariaDB's FIND_IN_SET, der er bygget til netop CSV-kolonner.
	@Query(nativeQuery = true, value = "SELECT * FROM user_roles WHERE FIND_IN_SET(:permission, requester_permission) > 0")
	List<UserRole> findByRequesterPermissionContaining(@Param("permission") String permission);

	@Query(nativeQuery = true, value = "SELECT * FROM user_roles WHERE FIND_IN_SET(:permission, approver_permission) > 0")
	List<UserRole> findByApproverPermissionContaining(@Param("permission") String permission);

	@Query(nativeQuery = true, value =
		"SELECT ur.* FROM user_roles ur " +
		"JOIN it_systems its ON its.id = ur.it_system_id " +
		"WHERE FIND_IN_SET('INHERIT', ur.requester_permission) > 0 " +
		"  AND FIND_IN_SET(:permission, its.requester_permission) > 0")
	List<UserRole> findInheritingByItSystemRequesterPermissionContaining(@Param("permission") String permission);

	// for production
	List<UserRole> getByDelegatedFromCvrNotNullAndItSystemIdentifierNot(String itSystemIdentifier);

	// for test
	List<UserRole> getByItSystemAndDelegatedFromCvrNotNull(ItSystem itSystem);

	Set<UserRole> findBySystemRoleAssignments_SystemRole(SystemRole systemRole);

	Set<UserRole> findBySystemRoleAssignments_SystemRoleIn(Collection<SystemRole> systemRoles);

	Set<UserRole> findAllByIdIn(Collection<Long> ids);

}
