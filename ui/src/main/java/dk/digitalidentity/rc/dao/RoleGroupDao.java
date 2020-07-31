package dk.digitalidentity.rc.dao;

import java.util.List;

import org.springframework.data.repository.CrudRepository;

import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.UserRole;

public interface RoleGroupDao extends CrudRepository<RoleGroup, String> {

	RoleGroup getById(long id);

	RoleGroup getByName(String name);

	List<RoleGroup> findAll();		
	List<RoleGroup> findByUserRoleAssignmentsUserRole(UserRole userRole);
	
	List<RoleGroup> findByOuInheritAllowedTrue();
}
