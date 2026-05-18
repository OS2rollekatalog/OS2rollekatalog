package dk.digitalidentity.rc.dao;

import dk.digitalidentity.rc.dao.model.Domain;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface UserDao extends CrudRepository<User, String>, JpaSpecificationExecutor<User> {

	// not really deprecated, but findAll() should only be used when we want
	// to access entities that have been deleted, so this is used as a warning
	// to the developer
	@Deprecated
	List<User> findAll();

	// same
	@Deprecated
	List<User> findByDomain(Domain domain);

	// same
	@Deprecated
	List<User> findByUserRoleAssignmentsUserRole(UserRole userRole);

	// same
	@Deprecated
	List<User> findByRoleGroupAssignmentsRoleGroup(RoleGroup role);

	// same
	@Deprecated
	List<User> findByExtUuidIn(Set<String> extUuids);

	// same
	@Deprecated
	List<User> findByDomainAndExtUuidIn(Domain domain, Set<String> extUuids);

	List<User> findByDomainAndDeletedFalse(Domain domain);
	List<User> findByDeletedFalse();
	List<User> findByDeletedTrue();
	long countByDeletedFalseAndUserRoleAssignmentsUserRole(UserRole userRole);
	long countByDeletedFalseAndRoleGroupAssignmentsRoleGroup(RoleGroup role);
	Optional<User> findByUuidAndDeletedFalse(String uuid);
	Optional<User> findByUuid(String uuid);

	List<User> findByUuidInAndDeletedFalse(Set<String> uuids);
	List<User> findByExtUuidAndDeletedFalse(String uuid);
	Optional<User> findByUserIdAndDomainAndDeletedFalse(String userId, Domain domain);
	List<User> findByUserIdInAndDomainAndDeletedFalse(List<String> userIds, Domain domain);

	// use the versions below that filters on active/inactive flag
	@Deprecated
	List<User> findByDeletedFalseAndRoleGroupAssignmentsRoleGroup(RoleGroup role);

	// use the versions below that filters on active/inactive flag
	@Deprecated
	List<User> findByDeletedFalseAndUserRoleAssignmentsUserRole(UserRole userRole);

	List<User> findByDeletedFalseAndUserRoleAssignmentsUserRoleAndUserRoleAssignmentsInactive(UserRole userRole, boolean inactive);
	List<User> findByDeletedFalseAndRoleGroupAssignmentsRoleGroupInAndRoleGroupAssignmentsInactive(List<RoleGroup> roleGroups, boolean inactive);

	User getTopByDeletedFalseOrderByLastUpdatedDesc();

	@Query(nativeQuery = true, value = "SELECT * FROM users u WHERE u.uuid IN (SELECT DISTINCT(manager) FROM ous)")
	List<User> getManagers();

	@Query(nativeQuery = true, value = "SELECT u.* " +
			"FROM users u" +
			" WHERE u.deleted = 0" +
			"  AND u.name LIKE CONCAT('%',?1,'%')" +
			" ORDER BY u.name LIMIT 10")
	List<User> findTop10ByName(@Param("name") String input);

	List<User> findByManagerSubstitutesSubstituteDeletedTrue();

	List<User> findByManagerSubstitutesSubstitute(User user);

	List<User> findByNemloginUuidNotNullAndDeletedFalseAndDisabledFalse();

	List<String> findUuidByNemloginUuidNotNullAndDeletedFalseAndDisabledFalse();

	List<User> findByUuidIn(Set<String> uuids);

	List<User> findByNameContainsOrUserIdContainsAndDeletedFalse(String name, String userId);

	List<User> findTop10ByDeletedFalse();

	// For disabled-brugere bruges disabledAt (den dag de blev deaktiveret).
	// For deleted-brugere bruges lastUpdated (den dag importen sidst rørte dem — dvs. dagen de blev soft-deleted, da
	// importen filtrerer allerede-deleted brugere fra på linje 1047 i OrganisationImporter og dermed ikke rører dem igen).
	@Query("SELECT DISTINCT u FROM users u " +
		"WHERE (u.disabled = true AND u.disabledAt IS NOT NULL AND u.disabledAt <= :cutoffDate) " +
		"   OR (u.deleted = true AND u.lastUpdated < :cutoffDateExclusiveUpper)")
	List<User> findDisabledUsersOlderThan(@Param("cutoffDate") LocalDate cutoffDate,
										  @Param("cutoffDateExclusiveUpper") Date cutoffDateExclusiveUpper);

// this forces EAGER loading of both relationsships, but since they are List<> that will not work, as List<> are by default sorted,
// so this needs to be Set<>'s or we need to add an ORDER BY to the WHERE statement. But it seems unneeded to change the LAZY loading
// into EAGER loading, as the relationships are already batched, so for now I'll comment this out   /BSG
//	@EntityGraph(attributePaths = {"userRoleAssignments", "roleGroupAssignments"})
	@Query("SELECT DISTINCT u FROM users u " +
		"WHERE (u.disabled = true AND u.disabledAt IS NOT NULL AND u.disabledAt <= :cutoffDate) " +
		"   OR (u.deleted = true AND u.lastUpdated < :cutoffDateExclusiveUpper)")
	List<User> findDisabledUsersOlderThanWithAssignments(@Param("cutoffDate") LocalDate cutoffDate,
														 @Param("cutoffDateExclusiveUpper") Date cutoffDateExclusiveUpper);
}
