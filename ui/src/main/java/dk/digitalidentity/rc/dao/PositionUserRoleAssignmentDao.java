package dk.digitalidentity.rc.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import dk.digitalidentity.rc.dao.model.PositionUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.UserRole;

public interface PositionUserRoleAssignmentDao extends JpaRepository<PositionUserRoleAssignment, Long> {
	List<PositionUserRoleAssignment> findByUserRole(UserRole userRole);
}
