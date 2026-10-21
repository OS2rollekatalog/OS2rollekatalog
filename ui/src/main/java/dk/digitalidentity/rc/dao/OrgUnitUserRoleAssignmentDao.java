package dk.digitalidentity.rc.dao;

import java.util.List;

import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.enums.ContainsTitles;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import dk.digitalidentity.rc.dao.model.OrgUnitUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.UserRole;
import java.util.Date;
import java.util.Optional;
import org.springframework.data.repository.query.Param;

public interface OrgUnitUserRoleAssignmentDao extends JpaRepository<OrgUnitUserRoleAssignment, Long> {
	List<OrgUnitUserRoleAssignment> findByUserRole(UserRole userRole);

	//List<OrgUnitUserRoleAssignment> findByUserRoleAndInactiveFalse(UserRole userRole);

	List<OrgUnitUserRoleAssignment> findByContainsTitles(ContainsTitles containsTitles);

	List<OrgUnitUserRoleAssignment> findByOrgUnitAndContainsTitles(OrgUnit orgUnit, ContainsTitles containsTitles);

	List<OrgUnitUserRoleAssignment> findByOrgUnitAndContainsTitlesAndInherit(OrgUnit orgUnit, ContainsTitles containsTitles, boolean inherit);

	Optional<OrgUnitUserRoleAssignment> findByIdAndOrgUnit_UuidAndUserRole_Id(long id, String orgUnitUuid, long userRoleId);

	/**
	 * Returns IDs of {@link OrgUnitUserRoleAssignment}s that don't yet have an open
	 * {@code historic_ou_assignment} row for their (ou_uuid, role_id) pair where
	 * {@code role_role_group_id IS NULL} (direct user-role assignment, not via role group).
	 * Used by the one-shot seed task that backfills the event-driven history table for
	 * existing assignments.
	 */
	@Query(value = """
			SELECT our.id
			FROM ou_roles our
			WHERE NOT EXISTS (
			    SELECT 1 FROM historic_ou_assignment h
			    WHERE h.ou_uuid = our.ou_uuid
			      AND h.role_id = our.role_id
			      AND h.role_role_group_id IS NULL
			      AND h.valid_to IS NULL
			)
			ORDER BY our.id
			""", nativeQuery = true)
	List<Long> findIdsMissingOpenHistoricRow(Pageable limit);

	/** The source timestamp for a role on an org unit, see UserUserRoleAssignmentDao. */
	@Query("SELECT a.assignedTimestamp FROM ou_roles a WHERE a.id = :id AND a.orgUnit.uuid = :orgUnitUuid AND a.userRole.id = :userRoleId")
	Optional<Date> findAssignedTimestamp(@Param("id") long id, @Param("orgUnitUuid") String orgUnitUuid,
			@Param("userRoleId") long userRoleId);

	List<OrgUnitUserRoleAssignment> findByContainsExceptedOusTrue();

	/** Active assignments without a stop date on active orgUnits - the candidates for the duplicate report. */
	@EntityGraph(attributePaths = { "orgUnit", "userRole", "userRole.itSystem" })
	@Query("SELECT a FROM ou_roles a WHERE a.orgUnit.active = true AND a.stopDate IS NULL AND a.inactive = false")
	List<OrgUnitUserRoleAssignment> findActiveWithoutStopDate();

	/*
	 * Each of these loads one filter list for every assignment that has one, in a single query. Run them before reading
	 * the filter lists of many assignments, so they are not loaded one assignment at a time.
	 */
	@Query("SELECT DISTINCT a FROM ou_roles a JOIN FETCH a.titles")
	List<OrgUnitUserRoleAssignment> fetchTitles();

	@Query("SELECT DISTINCT a FROM ou_roles a JOIN FETCH a.functions")
	List<OrgUnitUserRoleAssignment> fetchFunctions();

	@Query("SELECT DISTINCT a FROM ou_roles a JOIN FETCH a.exceptedUsers")
	List<OrgUnitUserRoleAssignment> fetchExceptedUsers();

	@Query("SELECT DISTINCT a FROM ou_roles a JOIN FETCH a.exceptedOus")
	List<OrgUnitUserRoleAssignment> fetchExceptedOus();
}
