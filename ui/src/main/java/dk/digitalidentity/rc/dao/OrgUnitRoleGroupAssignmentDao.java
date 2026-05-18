package dk.digitalidentity.rc.dao;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import dk.digitalidentity.rc.dao.model.OrgUnitRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.RoleGroup;

public interface OrgUnitRoleGroupAssignmentDao extends JpaRepository<OrgUnitRoleGroupAssignment, Long> {
	List<OrgUnitRoleGroupAssignment> findByRoleGroup(RoleGroup roleGroup);
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
}
