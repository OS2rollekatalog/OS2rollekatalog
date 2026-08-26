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

	@Query(nativeQuery = true, value = "SELECT * FROM user_roles WHERE FIND_IN_SET(:permission, approver_permission) > 0")
	List<UserRole> findByApproverPermissionContaining(@Param("permission") String permission);

	/**
	 * Returns all UserRoles requestable by a user whose qualifying permissions are expressed as
	 * boolean flags. The three-level hierarchy (role → IT system → global) is resolved in SQL:
	 *
	 * <ul>
	 *   <li>Role has any of the permitted values directly → included.</li>
	 *   <li>Role has INHERIT → fall through to IT system:
	 *     <ul>
	 *       <li>IT system has any of the permitted values → included.</li>
	 *       <li>IT system has INHERIT ({@code includeNullItSystem}) → included because the chain
	 *           reaches global settings, which the caller already checked allows this user.</li>
	 *       <li>IT system has an empty/null permission list → excluded, same as an explicit NONE.</li>
	 *     </ul>
	 *   </li>
	 *   <li>Role has INHERIT but no IT system → excluded (matches Java's {@code null} guard).</li>
	 * </ul>
	 */
	@Query(nativeQuery = true, value = """
		SELECT ur.* FROM user_roles ur
		LEFT JOIN it_systems its ON its.id = ur.it_system_id
		WHERE
		    (    (:hasEmployee    AND FIND_IN_SET('EMPLOYEE',            COALESCE(ur.requester_permission, '')) > 0)
		      OR (:hasManager     AND FIND_IN_SET('MANAGERORSUBSTITUTE', COALESCE(ur.requester_permission, '')) > 0)
		      OR (:hasAuthorized  AND FIND_IN_SET('AUTHORIZED',          COALESCE(ur.requester_permission, '')) > 0)
		      OR (:hasAuthResp    AND FIND_IN_SET('AUTHRESPONSIBLE',     COALESCE(ur.requester_permission, '')) > 0)
		      OR (:hasAdmin       AND FIND_IN_SET('ADMIN',               COALESCE(ur.requester_permission, '')) > 0)
		    )
		    OR (
		        FIND_IN_SET('INHERIT', COALESCE(ur.requester_permission, '')) > 0
		        AND its.id IS NOT NULL
		        AND (    (:hasEmployee    AND FIND_IN_SET('EMPLOYEE',            COALESCE(its.requester_permission, '')) > 0)
		              OR (:hasManager     AND FIND_IN_SET('MANAGERORSUBSTITUTE', COALESCE(its.requester_permission, '')) > 0)
		              OR (:hasAuthorized  AND FIND_IN_SET('AUTHORIZED',          COALESCE(its.requester_permission, '')) > 0)
		              OR (:hasAuthResp    AND FIND_IN_SET('AUTHRESPONSIBLE',     COALESCE(its.requester_permission, '')) > 0)
		              OR (:hasAdmin       AND FIND_IN_SET('ADMIN',               COALESCE(its.requester_permission, '')) > 0)
		              OR (:includeNullItSystem AND FIND_IN_SET('INHERIT', COALESCE(its.requester_permission, '')) > 0)
		        )
		    )
		""")
	List<UserRole> findRequestableRoles(
		@Param("hasEmployee")       boolean hasEmployee,
		@Param("hasManager")        boolean hasManager,
		@Param("hasAuthorized")     boolean hasAuthorized,
		@Param("hasAuthResp")       boolean hasAuthResp,
		@Param("hasAdmin")          boolean hasAdmin,
		@Param("includeNullItSystem") boolean includeNullItSystem
	);

	// for production
	List<UserRole> getByDelegatedFromCvrNotNullAndItSystemIdentifierNot(String itSystemIdentifier);

	// for test
	List<UserRole> getByItSystemAndDelegatedFromCvrNotNull(ItSystem itSystem);

	Set<UserRole> findBySystemRoleAssignments_SystemRole(SystemRole systemRole);

	Set<UserRole> findBySystemRoleAssignments_SystemRoleIn(Collection<SystemRole> systemRoles);

	Set<UserRole> findAllByIdIn(Collection<Long> ids);

	List<UserRole> findByItSystemIn(Collection<ItSystem> itSystems);

}
