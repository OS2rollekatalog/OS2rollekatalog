package dk.digitalidentity.rc.dao;

import dk.digitalidentity.rc.dao.model.SystemRoleAssignment;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignmentConstraintValue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SystemRoleAssignmentConstraintValueDao extends JpaRepository<SystemRoleAssignmentConstraintValue, Long> {

	List<SystemRoleAssignmentConstraintValue> findBySystemRoleAssignment(SystemRoleAssignment systemRoleAssignment);
}
