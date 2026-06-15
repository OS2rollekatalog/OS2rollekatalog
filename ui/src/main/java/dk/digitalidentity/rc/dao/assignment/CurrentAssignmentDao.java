package dk.digitalidentity.rc.dao.assignment;

import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.assignment.CurrentAssignment;
import dk.digitalidentity.rc.dao.model.assignment.CurrentAssignmentSmallProjection;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface CurrentAssignmentDao extends JpaRepository<CurrentAssignment, Long> {

	@Query("""
	  SELECT COUNT(*) FROM CurrentAssignment ca
	  WHERE ca.user = :user
	    AND ca.userRole = :userRole
	    AND (ca.startDate IS NULL OR ca.startDate <= :now)
	    AND (ca.stopDate IS NULL OR ca.stopDate >= :now)
	""")
	long countByUserRole(User user, UserRole userRole, LocalDate now);

	@Query("""
	  SELECT ca FROM CurrentAssignment ca
	  WHERE ca.user = :user
	    AND (ca.startDate IS NULL OR ca.startDate <= :now)
	    AND (ca.stopDate IS NULL OR ca.stopDate >= :now)
	""")
	Set<CurrentAssignment> findByUser(User user, LocalDate now);

	@Query("""
	  SELECT ca FROM CurrentAssignment ca
	  WHERE ca.user = :user
	""")
	Set<CurrentAssignment> findByUser(User user);

	@Query("""
	  SELECT ca FROM CurrentAssignment ca
	  WHERE ca.user IN :users
	""")
	Set<CurrentAssignment> findByUserIn(Collection<User> users);

	/** Finder alle assignments for en bruger med userRole og itSystem eager-loadet for at undgå N+1 queries. */
	@EntityGraph(attributePaths = {"userRole", "userRole.itSystem"})
	@Query("SELECT ca FROM CurrentAssignment ca WHERE ca.user.uuid = :userUuid")
	Set<CurrentAssignment> findByUserUuidWithEagerRolesAndSystems(String userUuid);

	@Query("""
	  SELECT ca FROM CurrentAssignment ca
	  WHERE ca.userRole = :userRole
	    AND (ca.startDate IS NULL OR ca.startDate <= :now)
	    AND (ca.stopDate IS NULL OR ca.stopDate >= :now)
	""")
	Set<CurrentAssignment> findByUserRole(UserRole userRole, LocalDate now);

	/**
	 * Som {@link #findByUserRole}, men returnerer kun user UUIDs.
	 * Bruges når kalderen er i en transaktion der efterfølgende sletter UserRole'en — så undgår vi
	 * at trække CurrentAssignment-entiteter med .userRole-reference ind i persistence-konteksten,
	 * hvor pre-flush-checket på næste iteration ellers ville se den slettede UserRole som transient.
	 */
	@Query("""
	  SELECT DISTINCT ca.user.uuid FROM CurrentAssignment ca
	  WHERE ca.userRole = :userRole
	    AND ca.user IS NOT NULL
	    AND (ca.startDate IS NULL OR ca.startDate <= :now)
	    AND (ca.stopDate IS NULL OR ca.stopDate >= :now)
	""")
	Set<String> findUserUuidsByUserRole(UserRole userRole, LocalDate now);

	@Query("""
	  SELECT ca FROM CurrentAssignment ca
	  WHERE ca.itSystem = :itSystem
	    AND (ca.startDate IS NULL OR ca.startDate <= :now)
	    AND (ca.stopDate IS NULL OR ca.stopDate >= :now)
	""")
	Set<CurrentAssignment> findByItSystem(ItSystem itSystem, LocalDate now);

	@Query("""
	  SELECT ca FROM CurrentAssignment ca
	  WHERE ca.roleGroup = :roleGroup
	    AND (ca.startDate IS NULL OR ca.startDate <= :now)
	    AND (ca.stopDate IS NULL OR ca.stopDate >= :now)
	""")
	Set<CurrentAssignment> findByRoleGroup(RoleGroup roleGroup, LocalDate now);

	/** Henter kun user UUIDs for en rollegruppe — undgår at loade hele CurrentAssignment-entiteter med alle relationer. */
	@Query("""
	  SELECT DISTINCT ca.user.uuid FROM CurrentAssignment ca
	  WHERE ca.roleGroup = :roleGroup
	    AND ca.user IS NOT NULL
	    AND (ca.startDate IS NULL OR ca.startDate <= :now)
	    AND (ca.stopDate IS NULL OR ca.stopDate >= :now)
	""")
	Set<String> findUserUuidsByRoleGroup(RoleGroup roleGroup, LocalDate now);

	@Query("""
	  SELECT ca FROM CurrentAssignment ca
	  WHERE ca.itSystem IN :itSystems
	    AND ca.user = :user
	    AND (ca.startDate IS NULL OR ca.startDate <= :now)
	    AND (ca.stopDate IS NULL OR ca.stopDate >= :now)
	""")
	Set<CurrentAssignment> findByUserAndItSystemIn(User user, Collection<ItSystem> itSystems, LocalDate now);

	/**
	 * Som {@link #findByUserAndItSystemIn}, men med eager fetch af de relationer der kræves
	 * til NemLog-in serialisering: systemRoleAssignments → constraintValues → constraintType
	 * og postponedConstraints. Undgår manuel "touch" af lazy collections efter load.
	 */
	@EntityGraph(attributePaths = {
		"userRole.systemRoleAssignments.systemRole",
		"userRole.systemRoleAssignments.constraintValues.constraintType",
		"postponedConstraints"
	})
	@Query("""
	  SELECT ca FROM CurrentAssignment ca
	  WHERE ca.itSystem IN :itSystems
	    AND ca.user = :user
	    AND (ca.startDate IS NULL OR ca.startDate <= :now)
	    AND (ca.stopDate IS NULL OR ca.stopDate >= :now)
	""")
	List<CurrentAssignment> findByUserAndItSystemInWithRoleDetails(User user, Collection<ItSystem> itSystems, LocalDate now);

	/** Finder direkte tildelte assignments pr. it-system (dvs. ikke via rollegruppe, enhed eller titel). */
	@Query("""
	  SELECT ca FROM CurrentAssignment ca
	  WHERE ca.itSystem = :itSystem
	    AND ca.roleGroup IS NULL
	    AND ca.orgUnit IS NULL
	    AND ca.title IS NULL
	    AND (ca.startDate IS NULL OR ca.startDate <= :now)
	    AND (ca.stopDate IS NULL OR ca.stopDate >= :now)
	""")
	Set<CurrentAssignment> findActiveDirectAssignmentsFromItSystem(ItSystem itSystem, LocalDate now);

	long countDistinctUserByUserRoleAndRoleGroupNullAndOrgUnitNullAndTitleNull(UserRole userRole);

	long countDistinctUserByRoleGroupAndOrgUnitNullAndTitleNull(RoleGroup roleGroup);

	// these also returns inactive assignments, which is okay, as they are used by the UI only
	Set<CurrentAssignment> findByRoleGroupAndUser(RoleGroup roleGroup, User user);
	Set<CurrentAssignment> findByUserUuidAndRoleGroupNotNull(String uuid);
	Set<CurrentAssignment> findByRoleGroupAndOrgUnitNullAndTitleNull(RoleGroup roleGroup);

	/**
	 * Finder direkte tildelte assignments (ikke via enhed/titel) for en userrole, hvor tildelingen er trådt i kraft.
	 * <p>
	 * Bruger eksplicit @Query fordi Spring Data JPA's derived query-parser ikke respekterer operator-præcedens korrekt
	 * ved kombinationen af AND og OR: uden parenteser tolkes
	 * {@code ...AndStartDateIsNullOrStartDateLessThanEqual} som
	 * {@code (... AND startDate IS NULL) OR startDate <= :startDate},
	 * hvilket returnerer alle assignments uanset userRole.
	 */
	@Query("""
	  SELECT ca FROM CurrentAssignment ca
	  WHERE ca.userRole = :userRole
	    AND ca.orgUnit IS NULL
	    AND ca.title IS NULL
	    AND (ca.startDate IS NULL OR ca.startDate <= :now)
	    AND (ca.stopDate IS NULL OR ca.stopDate >= :now)
	""")
	Set<CurrentAssignment> findActiveAssignedDirect(UserRole userRole, LocalDate now);

	/** Finder direkte tildelte assignments pr. userrole (dvs. ikke via rollegruppe, enhed eller titel). */
	@Query("""
	  SELECT ca FROM CurrentAssignment ca
	  WHERE ca.userRole = :userRole
	    AND ca.roleGroup IS NULL
	    AND ca.orgUnit IS NULL
	    AND ca.title IS NULL
	    AND (ca.startDate IS NULL OR ca.startDate <= :now)
	    AND (ca.stopDate IS NULL OR ca.stopDate >= :now)
	""")
	Set<CurrentAssignment> findActiveAssignedDirectUserRoleOnly(UserRole userRole, LocalDate now);

	/**
	 * Som {@link #findActiveAssignedDirectUserRoleOnly}, men returnerer kun User-projektionen.
	 * Bruges når kalderen er i en transaktion der efterfølgende sletter UserRole'en — så undgår vi
	 * at trække CurrentAssignment-entiteter med .userRole-reference ind i persistence-konteksten,
	 * hvor pre-flush cascade-checket ellers ville se den slettede UserRole som transient.
	 */
	@Query("""
	  SELECT DISTINCT ca.user FROM CurrentAssignment ca
	  WHERE ca.userRole = :userRole
	    AND ca.roleGroup IS NULL
	    AND ca.orgUnit IS NULL
	    AND ca.title IS NULL
	    AND (ca.startDate IS NULL OR ca.startDate <= :now)
	    AND (ca.stopDate IS NULL OR ca.stopDate >= :now)
	""")
	Set<User> findActiveUsersByDirectlyAssignedUserRoleOnly(UserRole userRole, LocalDate now);

	/**
	 * Finder assignments for en userrole, hvor tildelingen er trådt i kraft.
	 * <p>
	 * Bruger eksplicit @Query af samme årsag som
	 * {@link #findActiveAssignedDirect}.
	 */
	@Query("""
	  SELECT ca FROM CurrentAssignment ca
	  WHERE ca.userRole = :userRole
	    AND (ca.startDate IS NULL OR ca.startDate <= :now)
	    AND (ca.stopDate IS NULL OR ca.stopDate >= :now)
	""")
	Set<CurrentAssignment> findActiveAssigned(UserRole userRole, LocalDate now);

	/**
	 * Finder assignments for en userrole, hvor tildelingen er trådt i kraft.
	 * <p>
	 * Bruger eksplicit @Query af samme årsag som
	 * {@link #findByUserRoleAndOrgUnitNullAndTitleNullAndStartDateIsNullOrStartDateLessThanEqual}.
	 */
	@Query("""
	  SELECT ca FROM CurrentAssignment ca
	  WHERE ca.userRole IN :userRoles
	    AND (ca.startDate IS NULL OR ca.startDate <= :now)
	    AND (ca.stopDate IS NULL OR ca.stopDate >= :now)
	""")
	Set<CurrentAssignment> findActiveAssigned(Collection<UserRole> userRoles, LocalDate now);

	@Query("""
	    SELECT ca.id AS id, ca.user.userId AS userId, ca.userRole.id AS userRoleId, ca.user.domain.id AS userDomainId
	    FROM CurrentAssignment ca
		WHERE ca.userRole IN :userRoles
	      AND (ca.startDate IS NULL OR ca.startDate <= :now)
	      AND (ca.stopDate IS NULL OR ca.stopDate >= :now)
	""")
	Set<CurrentAssignmentSmallProjection> findActiveAssignedAsProjection(Collection<UserRole> userRoles, LocalDate now);
	
	/**
	 * Finder assignments for en rollegruppe, hvor tildelingen er trådt i kraft.
	 * <p>
	 * Bruger eksplicit @Query af samme årsag som
	 * {@link #findActiveAssignedDirect}.
	 */
	@Query("""
	  SELECT ca FROM CurrentAssignment ca
	  WHERE ca.roleGroup = :roleGroup
	    AND (ca.startDate IS NULL OR ca.startDate <= :now)
	    AND (ca.stopDate IS NULL OR ca.stopDate >= :now)
	""")
	Set<CurrentAssignment> findActiveAssignedThroughRoleGroup(RoleGroup roleGroup, LocalDate now);

	// these return inactive assignments, and are used by the UI where this is okay
	Set<CurrentAssignment> findByUserRoleAndOrgUnitNullAndTitleNull(UserRole userRole);
	Set<CurrentAssignment> findByUserRoleAndUser(UserRole userRole, User user);

	/**
	 * Finds direct assignments of RoleGroups that are active for a given User
	 * <p>
	 * Bruger eksplicit @Query af samme årsag som
	 * {@link #findActiveAssignedDirect}.
	 */
	@Query("""
	  SELECT ca FROM CurrentAssignment ca
	  WHERE ca.user = :user
	    AND ca.roleGroup IS NULL
	    AND ca.orgUnit IS NULL
	    AND ca.title IS NULL
	    AND (ca.startDate IS NULL OR ca.startDate <= :now)
	    AND (ca.stopDate IS NULL OR ca.stopDate >= :now)
	""")
	Set<CurrentAssignment> findActiveDirectAssignedThroughUserRoles(User user, LocalDate now);

	/**
	 * Finds direct assignments of RoleGroups that are active for a given User
	 * <p>
	 * Bruger eksplicit @Query af samme årsag som
	 * {@link #findActiveAssignedDirect}.
	 */
	@Query("""
	  SELECT ca FROM CurrentAssignment ca
	  WHERE ca.user = :user
	    AND ca.roleGroup IS NULL
	    AND ca.orgUnit IS NULL
	    AND ca.title IS NULL
	""")
	Set<CurrentAssignment> findDirectAssignedThroughUserRolesIncludingInactive(User user);

	/**
	 * Finds direct assignments of RoleGroups that are active for a given User
	 * <p>
	 * Bruger eksplicit @Query af samme årsag som
	 * {@link #findActiveAssignedDirect}.
	 */
	@Query("""
	  SELECT ca FROM CurrentAssignment ca
	  WHERE ca.user = :user
	    AND ca.roleGroup IS NOT NULL
	    AND ca.orgUnit IS NULL
	    AND ca.title IS NULL
	    AND (ca.startDate IS NULL OR ca.startDate <= :now)
	    AND (ca.stopDate IS NULL OR ca.stopDate >= :now)
	""")
	Set<CurrentAssignment> findActiveDirectAssignedThroughRoleGroups(User user, LocalDate now);

	@org.springframework.data.jpa.repository.Modifying
	@org.springframework.data.jpa.repository.Query("DELETE FROM CurrentAssignment ca WHERE ca.id IN :ids")
	void deleteByIdIn(@org.springframework.data.repository.query.Param("ids") Collection<Long> ids);

	boolean existsByUser_UuidAndUserRole_IdAndRoleGroupNullAndOrgUnitNullAndTitleNull(String userUuid, Long userRoleId);

	@Query("SELECT DISTINCT ca.userRole.id FROM CurrentAssignment ca")
	Set<Long> findDistinctUserRoleIds();

	/**
	 * Finder UUIDs på brugere der er deleted=true men stadig har current_assignment-rækker.
	 * Bruges af cleanup-tasken til at processere bagudrettet stale data efter at calculatoren
	 * har fået invariantet "deleted=true => ingen current_assignment-rækker".
	 */
	@Query("SELECT DISTINCT ca.user.uuid FROM CurrentAssignment ca WHERE ca.user.deleted = true")
	List<String> findUuidsOfDeletedUsersWithCurrentAssignments(Pageable pageable);

	/**
	 * Finder assignments for et it-system, hvor tildelingen er trådt i kraft.
	 * <p>
	 * Bruger eksplicit @Query af samme årsag som
	 * {@link #findActiveAssignedDirect}.
	 */
	@Query("""
	  SELECT ca FROM CurrentAssignment ca
	  WHERE ca.itSystem = :itSystem
	    AND (ca.startDate IS NULL OR ca.startDate <= :now)
	    AND (ca.stopDate IS NULL OR ca.stopDate >= :now)
	""")
	Set<CurrentAssignment> findActiveAssignmentsForItSystem(ItSystem itSystem, LocalDate now);

	/**
	 * Finder assignments for en liste af it-systemer, hvor tildelingen er trådt i kraft.
	 * <p>
	 * Bruger eksplicit @Query af samme årsag som
	 * {@link #findActiveAssignedDirect}.
	 */
	@Query("""
	  SELECT ca FROM CurrentAssignment ca
	  WHERE ca.itSystem IN :itSystems
	    AND (ca.startDate IS NULL OR ca.startDate <= :now)
	    AND (ca.stopDate IS NULL OR ca.stopDate >= :now)
	""")
	Set<CurrentAssignment> findActiveAssignmentsForItSystems(List<ItSystem> itSystems, LocalDate now);

	Set<CurrentAssignment> findByStartDateAndItSystem(LocalDate startDate, ItSystem itSystem);
}
