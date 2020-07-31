package dk.digitalidentity.rc.dao;

import java.util.List;

import org.springframework.data.repository.CrudRepository;

import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.UserRole;

public interface PositionDao extends CrudRepository<Position, String> {

	List<Position> findAll();
	
	List<Position> findByRoleGroupAssignmentsRoleGroup(RoleGroup role);
	List<Position> findByUserRoleAssignmentsUserRole(UserRole role);

	Position getById(long id);

	List<Position> findByOrgUnit(OrgUnit ou);
}