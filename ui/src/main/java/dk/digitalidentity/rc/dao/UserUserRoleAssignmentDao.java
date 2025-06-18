package dk.digitalidentity.rc.dao;

import java.util.List;

import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.UserRoleGroupAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.UserUserRoleAssignment;

public interface UserUserRoleAssignmentDao extends JpaRepository<UserUserRoleAssignment, Long> {
	List<UserUserRoleAssignment> findByUserRole(UserRole userRole);

	List<UserUserRoleAssignment> findByOrgUnitAndInactiveFalse(OrgUnit orgUnit);

	//List<UserUserRoleAssignment> findByUserRoleAndInactiveFalse(UserRole userRole);
}
