package dk.digitalidentity.rc.dao;

import java.util.List;

import dk.digitalidentity.rc.dao.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.UserUserRoleAssignment;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserUserRoleAssignmentDao extends JpaRepository<UserUserRoleAssignment, Long> {
	List<UserUserRoleAssignment> findByOrgUnitAndInactiveFalse(OrgUnit orgUnit);

	@Query("""
		SELECT COUNT(m) > 0 FROM user_roles_mapping m
		JOIN m.userRole ur
		JOIN ur.systemRoleAssignments sra
		JOIN sra.systemRole sr
		WHERE m.user = :user
		AND m.inactive = false
		AND sr.identifier = :identifier
		""")
	boolean existsByUserHavingSystemRoleIdentifier(
		@Param("user") User user,
		@Param("identifier") String identifier);
}
