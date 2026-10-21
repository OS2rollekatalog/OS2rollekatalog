package dk.digitalidentity.rc.dao;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import dk.digitalidentity.rc.dao.model.OrgUnitRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import java.util.Date;
import java.util.Optional;
import org.springframework.data.repository.query.Param;

public interface OrgUnitRoleGroupAssignmentDao extends JpaRepository<OrgUnitRoleGroupAssignment, Long> {
	List<OrgUnitRoleGroupAssignment> findByRoleGroup(RoleGroup roleGroup);

	Optional<OrgUnitRoleGroupAssignment> findByIdAndOrgUnit_UuidAndRoleGroup_Id(long id, String orgUnitUuid, long roleGroupId);
	//List<OrgUnitRoleGroupAssignment> findByRoleGroupAndInactiveFalse(RoleGroup roleGroup);

	/**
	 * Returns IDs of {@link OrgUnitRoleGroupAssignment}s where at least one of the user-roles
	 * in the assigned role group is missing an open {@code historic_ou_assignment} row.
	 * Used by the one-shot seed task that backfills the event-driven history table for
	 * existing assignments.
	 */
	@Query(value = """
			SELECT DISTINCT ourg.id
			FROM ou_rolegroups ourg
			JOIN rolegroup_roles rgr ON rgr.rolegroup_id = ourg.rolegroup_id
			WHERE NOT EXISTS (
			    SELECT 1 FROM historic_ou_assignment h
			    WHERE h.ou_uuid = ourg.ou_uuid
			      AND h.role_role_group_id = ourg.rolegroup_id
			      AND h.role_id = rgr.role_id
			      AND h.valid_to IS NULL
			)
			ORDER BY ourg.id
			""", nativeQuery = true)
	List<Long> findIdsMissingOpenHistoricRow(Pageable limit);

	/** The source timestamp for a role group on an org unit, see UserUserRoleAssignmentDao. */
	@Query("SELECT a.assignedTimestamp FROM ou_rolegroups a WHERE a.id = :id AND a.orgUnit.uuid = :orgUnitUuid AND a.roleGroup.id = :roleGroupId")
	Optional<Date> findAssignedTimestamp(@Param("id") long id, @Param("orgUnitUuid") String orgUnitUuid,
			@Param("roleGroupId") long roleGroupId);

	List<OrgUnitRoleGroupAssignment> findByContainsExceptedOusTrue();

	/** Active assignments without a stop date on active orgUnits - the candidates for the duplicate report. */
	@EntityGraph(attributePaths = { "orgUnit", "roleGroup" })
	@Query("SELECT a FROM ou_rolegroups a WHERE a.orgUnit.active = true AND a.stopDate IS NULL AND a.inactive = false")
	List<OrgUnitRoleGroupAssignment> findActiveWithoutStopDate();

	/*
	 * Each of these loads one filter list for every assignment that has one, in a single query. Run them before reading
	 * the filter lists of many assignments, so they are not loaded one assignment at a time.
	 */
	@Query("SELECT DISTINCT a FROM ou_rolegroups a JOIN FETCH a.titles")
	List<OrgUnitRoleGroupAssignment> fetchTitles();

	@Query("SELECT DISTINCT a FROM ou_rolegroups a JOIN FETCH a.functions")
	List<OrgUnitRoleGroupAssignment> fetchFunctions();

	@Query("SELECT DISTINCT a FROM ou_rolegroups a JOIN FETCH a.exceptedUsers")
	List<OrgUnitRoleGroupAssignment> fetchExceptedUsers();

	@Query("SELECT DISTINCT a FROM ou_rolegroups a JOIN FETCH a.exceptedOus")
	List<OrgUnitRoleGroupAssignment> fetchExceptedOus();
}
