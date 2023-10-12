package dk.digitalidentity.rc.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.CrudRepository;

import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;

public interface OrgUnitDao extends CrudRepository<OrgUnit, String> {
	
	// not really deprecated, but findAll() should only be used when we want to
	// access entities that have been deleted, so this is used as a warning to
	// the developer
	@Deprecated
	List<OrgUnit> findAll();
	
	// same
	@Deprecated
	List<OrgUnit> findByUserRoleAssignmentsUserRole(UserRole role);
	
	// same
	@Deprecated
	List<OrgUnit> findByRoleGroupAssignmentsRoleGroup(RoleGroup role);

	List<OrgUnit> findByActiveTrue();
	long countByActiveTrueAndUserRoleAssignmentsUserRole(UserRole role);	
	long countByActiveTrueAndRoleGroupAssignmentsRoleGroup(RoleGroup role);
	OrgUnit findByActiveTrueAndParentIsNull();	
	OrgUnit findByUuidAndActiveTrue(String uuid);
	
	@Deprecated
	List<OrgUnit> findByActiveTrueAndUserRoleAssignmentsUserRole(UserRole role);
	List<OrgUnit> findByActiveTrueAndUserRoleAssignmentsUserRoleAndUserRoleAssignmentsInactive(UserRole userRole, boolean inactive);
	List<OrgUnit> findByActiveTrueAndUserRoleAssignmentsUserRoleAndUserRoleAssignmentsInheritAndUserRoleAssignmentsInactive(UserRole role, boolean inherit, boolean inactive);

	@Deprecated
	List<OrgUnit> findByActiveTrueAndRoleGroupAssignmentsRoleGroup(RoleGroup role);
	List<OrgUnit> findByActiveTrueAndRoleGroupAssignmentsRoleGroupAndRoleGroupAssignmentsInactive(RoleGroup roleGroup, boolean inactive);
	List<OrgUnit> findByActiveTrueAndRoleGroupAssignmentsRoleGroupAndRoleGroupAssignmentsInheritAndRoleGroupAssignmentsInactive(RoleGroup role, boolean inherit, boolean inactive);
	List<OrgUnit> findByManager(User user);
	List<OrgUnit> findByActiveTrueAndNextAttestationNotNull();
	
	List<OrgUnit> findByAuthorizationManagersUser(User user);

	List<OrgUnit> findByActiveTrueAndManagerNotNull();

	List<OrgUnit> findByUuidIn(List<String> uuids);
}
