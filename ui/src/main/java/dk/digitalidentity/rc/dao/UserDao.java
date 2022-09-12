package dk.digitalidentity.rc.dao;

import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
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
	
	// same
	@Deprecated
	List<User> findByExtUuidIn(Set<String> extUuids);
	
	List<User> findByCprAndDeletedFalse(String cpr);
	List<User> findByDeletedFalse();
	List<User> findByDeletedTrue();
	long countByDeletedFalseAndUserRoleAssignmentsUserRole(UserRole userRole);
	long countByDeletedFalseAndRoleGroupAssignmentsRoleGroup(RoleGroup role);
	User findByUuidAndDeletedFalse(String uuid);
	List<User> findByUuidInAndDeletedFalse(Set<String> uuids);
	List<User> findByExtUuidAndDeletedFalse(String uuid);
	User findByUserIdAndDeletedFalse(String userId);
	
	// use the versions below that filters on active/inactive flag
	@Deprecated
	List<User> findByDeletedFalseAndRoleGroupAssignmentsRoleGroup(RoleGroup role);

	// use the versions below that filters on active/inactive flag
	@Deprecated
	List<User> findByDeletedFalseAndUserRoleAssignmentsUserRole(UserRole userRole);

	List<User> findByDeletedFalseAndUserRoleAssignmentsUserRoleAndUserRoleAssignmentsInactive(UserRole userRole, boolean inactive);
	List<User> findByDeletedFalseAndRoleGroupAssignmentsRoleGroupAndRoleGroupAssignmentsInactive(RoleGroup roleGroup, boolean inactive);
	
	User getTopByDeletedFalseOrderByLastUpdatedDesc();

	@Query(nativeQuery = true, value = "SELECT * FROM users u WHERE u.uuid IN (SELECT DISTINCT(manager) FROM ous)")
	List<User> getManagers();

	@Query(nativeQuery = true, value = "SELECT u.* " +
			"FROM users u" +
			" WHERE u.deleted = 0" +
			"  AND u.name LIKE CONCAT('%',?1,'%')" +
			" ORDER BY u.name LIMIT 10")
	List<User> findTop10ByName(@Param("name") String input);

	List<User> findByManagerSubstitute(User user);

	List<User> findByManagerSubstituteDeletedTrue();

	@Modifying
	@Query("update users u set u.managerSubstitute = null where u.managerSubstitute = :manager")
	void deleteManagerSubstituteAssignment(@Param("manager") User user);
}
