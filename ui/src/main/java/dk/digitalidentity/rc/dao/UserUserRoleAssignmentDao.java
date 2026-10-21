package dk.digitalidentity.rc.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.UserUserRoleAssignment;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Date;
import java.util.Optional;

public interface UserUserRoleAssignmentDao extends JpaRepository<UserUserRoleAssignment, Long> {
	List<UserUserRoleAssignment> findByOrgUnitAndInactiveFalse(OrgUnit orgUnit);

	/**
	 * The source timestamp for a directly assigned role. User and role are matched too: assignment_id
	 * on current_assignment is an id from one of four tables, so a bare id lookup could hit a row in
	 * this one that belongs to a different assignment entirely.
	 */
	@Query("SELECT a.assignedTimestamp FROM user_roles_mapping a WHERE a.id = :id AND a.user.uuid = :userUuid AND a.userRole.id = :userRoleId")
	Optional<Date> findAssignedTimestamp(@Param("id") long id, @Param("userUuid") String userUuid,
			@Param("userRoleId") long userRoleId);
}
