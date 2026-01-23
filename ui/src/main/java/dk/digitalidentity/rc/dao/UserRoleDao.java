package dk.digitalidentity.rc.dao;

import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.SystemRole;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.rolerequest.model.enums.ApprovableBy;
import dk.digitalidentity.rc.rolerequest.model.enums.RequestableBy;
import org.springframework.data.repository.CrudRepository;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface UserRoleDao extends CrudRepository<UserRole, Long> {
	UserRole getByNameAndItSystem(String name, ItSystem itSystem);
	List<UserRole> findAll();
	UserRole getByIdentifier(String identifier);
	List<UserRole> findByItSystem(ItSystem itSystem);
	<S extends UserRole> S save(S entity);
	UserRole getByItSystemAndIdentifier(ItSystem itSystem, String identifier);
	int countBySystemRoleAssignmentsSystemRole(SystemRole systemRole);
	List<UserRole> findBySensitiveRoleTrue();
	List<UserRole> findByLinkedSystemRoleNotNull();

	// for production
	List<UserRole> getByDelegatedFromCvrNotNullAndItSystemIdentifierNot(String itSystemIdentifier);

	// for test
	List<UserRole> getByItSystemAndDelegatedFromCvrNotNull(ItSystem itSystem);

	List<UserRole> findByRequesterPermissionIn(Collection<RequestableBy> requesterPermissions);
	List<UserRole> findByRequesterPermissionAndItSystem_RequesterPermissionIn(RequestableBy requesterPermission, Collection<RequestableBy> requesterPermissions);
	List<UserRole> findByRequesterPermissionAndItSystem_RequesterPermissionInOrItSystem_RequesterPermissionNull(RequestableBy requesterPermission, Collection<RequestableBy> requesterPermissions);

	List<UserRole> findByApproverPermissionIn(List<ApprovableBy> permissions);

	Set<UserRole> findBySystemRoleAssignments_SystemRole(SystemRole systemRole);

}
