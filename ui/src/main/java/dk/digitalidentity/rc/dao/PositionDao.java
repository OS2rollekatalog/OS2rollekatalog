package dk.digitalidentity.rc.dao;

import java.util.List;

import org.springframework.data.repository.CrudRepository;

import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.Title;
import dk.digitalidentity.rc.dao.model.UserRole;

public interface PositionDao extends CrudRepository<Position, String> {

	List<Position> findAll();
	
	@Deprecated
	List<Position> findByRoleGroupAssignmentsRoleGroup(RoleGroup role);
	
	@Deprecated
	List<Position> findByUserRoleAssignmentsUserRole(UserRole role);

	List<Position> findByRoleGroupAssignmentsRoleGroupAndRoleGroupAssignmentsInactive(RoleGroup role, boolean inactive);
	List<Position> findByUserRoleAssignmentsUserRoleAndUserRoleAssignmentsInactive(UserRole userRole, boolean inactive);

	Position getById(long id);

	List<Position> findByOrgUnit(OrgUnit ou);

	List<Position> findByTitle(Title title);
}