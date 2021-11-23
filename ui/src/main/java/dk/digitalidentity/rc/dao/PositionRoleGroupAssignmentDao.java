package dk.digitalidentity.rc.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import dk.digitalidentity.rc.dao.model.PositionRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.RoleGroup;

public interface PositionRoleGroupAssignmentDao extends JpaRepository<PositionRoleGroupAssignment, Long> {
	List<PositionRoleGroupAssignment> findByRoleGroup(RoleGroup roleGroup);
}
