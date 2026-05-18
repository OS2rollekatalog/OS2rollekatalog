package dk.digitalidentity.rc.dao;

import java.util.List;

import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.enums.ContainsTitles;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import dk.digitalidentity.rc.dao.model.OrgUnitUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.UserRole;

public interface OrgUnitUserRoleAssignmentDao extends JpaRepository<OrgUnitUserRoleAssignment, Long> {
	List<OrgUnitUserRoleAssignment> findByUserRole(UserRole userRole);

	//List<OrgUnitUserRoleAssignment> findByUserRoleAndInactiveFalse(UserRole userRole);

	List<OrgUnitUserRoleAssignment> findByContainsTitles(ContainsTitles containsTitles);

	List<OrgUnitUserRoleAssignment> findByOrgUnitAndContainsTitles(OrgUnit orgUnit, ContainsTitles containsTitles);

	List<OrgUnitUserRoleAssignment> findByOrgUnitAndContainsTitlesAndInherit(OrgUnit orgUnit, ContainsTitles containsTitles, boolean inherit);

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
}
