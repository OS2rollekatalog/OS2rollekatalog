package dk.digitalidentity.rc.dao;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.projections.FunctionOrgUnit;
import dk.digitalidentity.rc.dao.projections.OrgUnitManagerName;
import dk.digitalidentity.rc.dao.projections.OrgUnitManagerUuid;
import dk.digitalidentity.rc.dao.projections.OrgUnitParentAndManager;
import dk.digitalidentity.rc.dao.projections.OrgUnitUuidNameAndParent;
import org.springframework.data.repository.query.Param;

public interface OrgUnitDao extends CrudRepository<OrgUnit, String> {

	// not really deprecated, but findAll() should only be used when we want to
	// access entities that have been deleted, so this is used as a warning to
	// the developer
	@Deprecated
	List<OrgUnit> findAll();

	// same
	@Deprecated
	List<OrgUnit> findByUserRoleAssignmentsUserRole(UserRole role);

	// same
	@Deprecated
	List<OrgUnit> findByRoleGroupAssignmentsRoleGroup(RoleGroup role);

	List<OrgUnit> findByActiveTrue();
	long countByActiveTrueAndUserRoleAssignmentsUserRole(UserRole role);
	long countByActiveTrueAndRoleGroupAssignmentsRoleGroup(RoleGroup role);
	OrgUnit findByActiveTrueAndParentIsNull();
	OrgUnit findByUuidAndActiveTrue(String uuid);

	@Deprecated
	List<OrgUnit> findByActiveTrueAndUserRoleAssignmentsUserRole(UserRole role);
	List<OrgUnit> findByActiveTrueAndUserRoleAssignmentsUserRoleAndUserRoleAssignmentsInactive(UserRole userRole, boolean inactive);
	List<OrgUnit> findByActiveTrueAndUserRoleAssignmentsUserRoleAndUserRoleAssignmentsInheritAndUserRoleAssignmentsInactive(UserRole role, boolean inherit, boolean inactive);

	@Deprecated
	List<OrgUnit> findByActiveTrueAndRoleGroupAssignmentsRoleGroup(RoleGroup role);
	List<OrgUnit> findByActiveTrueAndRoleGroupAssignmentsRoleGroupInAndRoleGroupAssignmentsInactive(List<RoleGroup> roleGroups, boolean inactive);
	List<OrgUnit> findByActiveTrueAndRoleGroupAssignmentsRoleGroupAndRoleGroupAssignmentsInheritAndRoleGroupAssignmentsInactive(RoleGroup role, boolean inherit, boolean inactive);
	List<OrgUnit> findByManager(User user);
	List<OrgUnit> findByActiveTrueAndNextAttestationNotNull();

	List<OrgUnit> findByAuthorizationManagersUser(User user);

	List<OrgUnit> findByActiveTrueAndManagerNotNull();

	List<OrgUnit> findByUuidIn(List<String> uuids);

	<T> List<T> findByUuidIn(Collection<String> uuids, Class<T> projectionType);

	Optional<OrgUnit> getByUuid(String uuid);

	Optional<OrgUnitManagerName> findByActiveTrueAndUuid(String uuid);

	List<OrgUnitManagerUuid> findByActiveTrueAndManagerNotNullAndUuidIn(List<String> uuids);


	List<OrgUnit> findByActiveTrueAndParentNull();

	@Query(value = """
		WITH RECURSIVE descendant_tree AS (
		    SELECT uuid, parent_uuid, active
		    FROM ous
		    WHERE manager = :managerUuid AND active = true

		    UNION ALL

		    SELECT o.uuid, o.parent_uuid, o.active
		    FROM ous o
		    INNER JOIN descendant_tree dt ON o.parent_uuid = dt.uuid
		    WHERE o.active = true
		)
		SELECT uuid FROM descendant_tree
		""", nativeQuery = true)
	Set<String> findDescendantOuUuidsWithEffectiveManager(@Param("managerUuid") String managerUuid);

	@Query(value = """
		WITH RECURSIVE descendant_tree AS (
		    SELECT uuid, parent_uuid, active
		    FROM ous
		    WHERE uuid = :ouUuid AND active = true

		    UNION ALL

		    SELECT o.uuid, o.parent_uuid, o.active
		    FROM ous o
		    INNER JOIN descendant_tree dt ON o.parent_uuid = dt.uuid
		    WHERE o.active = true
		)
		SELECT uuid FROM descendant_tree
		""", nativeQuery = true)
	Set<String> findDescendantOuUuids(@Param("ouUuid") String ouUuid);

	// UNION (not UNION ALL) terminates the recursion on cyclic OU data
	@Query(value = """
		WITH RECURSIVE ou_tree AS (
		    SELECT uuid
		    FROM ous
		    WHERE uuid IN (:rootUuids)

		    UNION

		    SELECT o.uuid
		    FROM ous o
		    INNER JOIN ou_tree t ON o.parent_uuid = t.uuid
		)
		SELECT uuid FROM ou_tree
		""", nativeQuery = true)
	Set<String> findOuUuidsIncludingDescendants(@Param("rootUuids") Collection<String> rootUuids);

    @Query(value = """
            WITH RECURSIVE ancestor_tree AS (
                SELECT *
                FROM ous
                WHERE uuid = :uuid

                UNION ALL

                SELECT ou.*
                FROM ous ou
                INNER JOIN ancestor_tree at ON ou.uuid = at.parent_uuid
            )
            SELECT * FROM ancestor_tree
            """,
            nativeQuery = true)
    List<OrgUnit> findWithAllAncestors(@Param("uuid") String uuid);

    @Query(value = """
            WITH RECURSIVE ancestor_tree AS (
                SELECT uuid, parent_uuid
                FROM ous
                WHERE uuid = :uuid

                UNION ALL

                SELECT ou.uuid, ou.parent_uuid
                FROM ous ou
                INNER JOIN ancestor_tree at ON ou.uuid = at.parent_uuid
            )
            SELECT uuid FROM ancestor_tree
            """,
            nativeQuery = true)
    List<String> findAllAncestorUuids(@Param("uuid") String uuid);

	@Query(value = """
    WITH RECURSIVE ou_hierarchy AS (
        SELECT uuid, parent_uuid, name, active, manager
        FROM ous
        WHERE uuid = :ouUuid AND active = true

        UNION ALL

        SELECT o.uuid, o.parent_uuid, o.name, o.active, o.manager
        FROM ous o
        INNER JOIN ou_hierarchy oh ON o.parent_uuid = oh.uuid
        WHERE o.active = true
    )
    SELECT DISTINCT u.uuid
    FROM users u
    WHERE u.deleted = false
    AND (
        EXISTS (
            SELECT 1
            FROM positions p
            INNER JOIN ou_hierarchy oh ON p.ou_uuid = oh.uuid
            WHERE p.user_uuid = u.uuid
        )
        OR EXISTS (
            SELECT 1
            FROM user_ou_function uof
            INNER JOIN ou_hierarchy oh ON uof.ou_uuid = oh.uuid
            WHERE uof.user_uuid = u.uuid
        )
        OR EXISTS (
            SELECT 1
            FROM ou_hierarchy oh
            WHERE oh.manager = u.uuid
        )
        OR EXISTS (
            SELECT 1
            FROM users_manager_substitute ums
            INNER JOIN ou_hierarchy oh ON ums.ou_uuid = oh.uuid
            WHERE ums.substitute_uuid = u.uuid
            AND ums.manager_uuid = oh.manager
        )
    )
    """, nativeQuery = true)
	Set<String> findUserUuidsByOrgUnitAndDescendants(@Param("ouUuid") String ouUuid);

	@Query(value = """
    SELECT DISTINCT u.uuid
    FROM users u
    WHERE u.deleted = false
    AND u.uuid IN (
        SELECT p.user_uuid
        FROM positions p
        WHERE p.ou_uuid = :ouUuid

        UNION

        SELECT uof.user_uuid
        FROM user_ou_function uof
        WHERE uof.ou_uuid = :ouUuid

        UNION

        SELECT o.manager
        FROM ous o
        WHERE o.uuid = :ouUuid
        AND o.manager IS NOT NULL

        UNION

        SELECT ums.substitute_uuid
        FROM users_manager_substitute ums
        INNER JOIN ous o ON ums.ou_uuid = o.uuid
        WHERE ums.ou_uuid = :ouUuid
        AND ums.manager_uuid = o.manager
    )
    """, nativeQuery = true)
	Set<String> findUserUuidsByOrgUnit(@Param("ouUuid") String ouUuid);


	<T> Set<T> findByUuid(String uuid, Class<T> projectionType);

	@Query("""
		SELECT new dk.digitalidentity.rc.dao.projections.OrgUnitUuidNameAndParent(ou.uuid, ou.name, parent.uuid)
		FROM ous ou LEFT JOIN ou.parent parent
		WHERE ou.uuid IN :uuids
		""")
	List<OrgUnitUuidNameAndParent> findUuidNameAndParentByUuidIn(@Param("uuids") Collection<String> uuids);

	@Query("""
		SELECT new dk.digitalidentity.rc.dao.projections.OrgUnitParentAndManager(ou.uuid, parent.uuid, manager.uuid)
		FROM ous ou LEFT JOIN ou.parent parent LEFT JOIN ou.manager manager
		""")
	List<OrgUnitParentAndManager> findAllParentAndManager();

	@Query("SELECT ou.uuid FROM ous ou WHERE ou.manager.uuid = :managerUuid")
	Set<String> findUuidsByManagerUuid(@Param("managerUuid") String managerUuid);

	@Query("""
		SELECT new dk.digitalidentity.rc.dao.projections.FunctionOrgUnit(f.function.uuid, f.orgUnit.uuid)
		FROM user_ou_function f
		WHERE f.user.uuid = :userUuid AND f.function.uuid IN :functionUuids
		""")
	List<FunctionOrgUnit> findFunctionOrgUnitsByUser(@Param("userUuid") String userUuid, @Param("functionUuids") Collection<String> functionUuids);
}
