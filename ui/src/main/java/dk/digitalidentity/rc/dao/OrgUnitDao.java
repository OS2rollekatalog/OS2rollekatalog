package dk.digitalidentity.rc.dao;

import java.util.List;

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
	List<OrgUnit> getByUserRoleAssignmentsUserRole(UserRole role);
	
	// same
	@Deprecated
	List<OrgUnit> getByRoleGroupAssignmentsRoleGroup(RoleGroup role);

	List<OrgUnit> findByActiveTrue();
	long countByActiveTrueAndUserRoleAssignmentsUserRole(UserRole role);	
	long countByActiveTrueAndRoleGroupAssignmentsRoleGroup(RoleGroup role);
	OrgUnit getByActiveTrueAndParentIsNull();	
	OrgUnit getByUuidAndActiveTrue(String uuid);
	List<OrgUnit> getByActiveTrue();
	
	@Deprecated
	List<OrgUnit> getByActiveTrueAndUserRoleAssignmentsUserRole(UserRole role);
	List<OrgUnit> getByActiveTrueAndUserRoleAssignmentsUserRoleAndUserRoleAssignmentsInactive(UserRole userRole, boolean inactive);
	List<OrgUnit> getByActiveTrueAndUserRoleAssignmentsUserRoleAndUserRoleAssignmentsInheritAndUserRoleAssignmentsInactive(UserRole role, boolean inherit, boolean inactive);

	@Deprecated
	List<OrgUnit> getByActiveTrueAndRoleGroupAssignmentsRoleGroup(RoleGroup role);
	List<OrgUnit> getByActiveTrueAndRoleGroupAssignmentsRoleGroupAndRoleGroupAssignmentsInactive(RoleGroup roleGroup, boolean inactive);
	List<OrgUnit> getByActiveTrueAndRoleGroupAssignmentsRoleGroupAndRoleGroupAssignmentsInheritAndRoleGroupAssignmentsInactive(RoleGroup role, boolean inherit, boolean inactive);
	List<OrgUnit> getByManager(User user);
	List<OrgUnit> getByActiveTrueAndNextAttestationNotNull();
	
	List<OrgUnit> getByAuthorizationManagersUser(User user);

	List<OrgUnit> getByActiveTrueAndManagerNotNull();
}
