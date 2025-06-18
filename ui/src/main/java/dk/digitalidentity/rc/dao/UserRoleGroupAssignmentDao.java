package dk.digitalidentity.rc.dao;

import java.util.List;

import dk.digitalidentity.rc.dao.model.OrgUnit;
import org.springframework.data.jpa.repository.JpaRepository;

import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.UserRoleGroupAssignment;

public interface UserRoleGroupAssignmentDao extends JpaRepository<UserRoleGroupAssignment, Long> {
	List<UserRoleGroupAssignment> findByRoleGroup(RoleGroup roleGroup);
	List<UserRoleGroupAssignment> findByOrgUnitAndInactiveFalse(OrgUnit orgUnit);

	//List<UserRoleGroupAssignment> findByRoleGroupAndInactiveFalse(RoleGroup roleGroup);
}
