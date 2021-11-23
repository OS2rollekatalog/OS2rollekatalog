package dk.digitalidentity.rc.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import dk.digitalidentity.rc.dao.model.OrgUnitUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.UserRole;

public interface OrgUnitUserRoleAssignmentDao extends JpaRepository<OrgUnitUserRoleAssignment, Long> {
	List<OrgUnitUserRoleAssignment> findByUserRole(UserRole userRole);
	//List<OrgUnitUserRoleAssignment> findByUserRoleAndInactiveFalse(UserRole userRole);
}
