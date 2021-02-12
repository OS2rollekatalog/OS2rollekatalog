package dk.digitalidentity.rc.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;

public interface UserDao extends CrudRepository<User, String>, JpaSpecificationExecutor<User> {

	// not really deprecated, but findAll() should only be used when we want
	// to access entities that have been deleted, so this is used as a warning
	// to the developer
	@Deprecated
	List<User> findAll();
	
	// same
	@Deprecated
	List<User> findByUserRoleAssignmentsUserRole(UserRole userRole);
	
	// same
	@Deprecated
	List<User> findByRoleGroupAssignmentsRoleGroup(RoleGroup role);
	
	List<User> findByCprAndActiveTrue(String cpr);
	List<User> getByActiveTrue();
	List<User> getByActiveFalse();
	long countByActiveTrueAndUserRoleAssignmentsUserRole(UserRole userRole);
	long countByActiveTrueAndRoleGroupAssignmentsRoleGroup(RoleGroup role);
	User getByUuidAndActiveTrue(String uuid);
	List<User> getByExtUuidAndActiveTrue(String uuid);
	User getByUserIdAndActiveTrue(String userId);
	
	@Deprecated
	List<User> findByActiveTrueAndRoleGroupAssignmentsRoleGroup(RoleGroup role);
	@Deprecated
	List<User> findByActiveTrueAndUserRoleAssignmentsUserRole(UserRole userRole);

	List<User> findByActiveTrueAndUserRoleAssignmentsUserRoleAndUserRoleAssignmentsInactive(UserRole userRole, boolean inactive);
	List<User> findByActiveTrueAndRoleGroupAssignmentsRoleGroupAndRoleGroupAssignmentsInactive(RoleGroup roleGroup, boolean inactive);

	User getTopByActiveTrueOrderByLastUpdatedDesc();

	@Query(nativeQuery = true, value = "SELECT * FROM users u WHERE u.uuid IN (SELECT DISTINCT(manager) FROM ous)")
	List<User> getManagers();

	@Query(nativeQuery = true, value = "SELECT u.* " +
			"FROM users u" +
			" WHERE u.active = 1" +
			"  AND u.name LIKE CONCAT('%',?1,'%')" +
			" ORDER BY u.name LIMIT 10")
	List<User> findTop10ByName(@Param("name") String input);

	List<User> getByManagerSubstitute(User user);
}
