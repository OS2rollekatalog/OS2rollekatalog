package dk.digitalidentity.rc.dao;

import java.util.List;

import org.springframework.data.repository.CrudRepository;

import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.UserRole;

public interface RoleGroupDao extends CrudRepository<RoleGroup, Long> {

	RoleGroup findById(long id);

	RoleGroup findByName(String name);

	List<RoleGroup> findAll();		
	
	List<RoleGroup> findByUserRoleAssignmentsUserRole(UserRole userRole);
}
