package dk.digitalidentity.rc.dao;

import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.Title;
import dk.digitalidentity.rc.dao.model.UserRole;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

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

	@Query("SELECT u.uuid FROM users u JOIN positions p ON p.user = u WHERE u.deleted = false AND p.orgUnit = ?1")
	List<String> findUserUuidByOrgUnit(OrgUnit orgUnit);
}