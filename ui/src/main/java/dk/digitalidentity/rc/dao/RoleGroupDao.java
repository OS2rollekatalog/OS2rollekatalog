package dk.digitalidentity.rc.dao;

import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.rolerequest.model.enums.ApproverOption;
import dk.digitalidentity.rc.rolerequest.model.enums.RequesterOption;
import org.springframework.data.repository.CrudRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RoleGroupDao extends CrudRepository<RoleGroup, Long> {

	Optional<RoleGroup> findByName(String name);

	List<RoleGroup> findAll();

	List<RoleGroup> findByUserRoleAssignmentsUserRole(UserRole userRole);

	List<RoleGroup> findByRequesterPermissionIn(Collection<RequesterOption> requesterPermissions);
	List<RoleGroup> findByApproverPermissionIn(Collection<ApproverOption> approverPermissions);
}
