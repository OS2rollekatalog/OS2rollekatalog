package dk.digitalidentity.rc.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import dk.digitalidentity.rc.dao.model.OrgUnitRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.RoleGroup;

public interface OrgUnitRoleGroupAssignmentDao extends JpaRepository<OrgUnitRoleGroupAssignment, Long> {
	List<OrgUnitRoleGroupAssignment> findByRoleGroup(RoleGroup roleGroup);
	//List<OrgUnitRoleGroupAssignment> findByRoleGroupAndInactiveFalse(RoleGroup roleGroup);
}
