package dk.digitalidentity.rc.dao;

import java.util.List;

import org.springframework.data.repository.CrudRepository;

import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.SystemRole;
import dk.digitalidentity.rc.dao.model.UserRole;

public interface UserRoleDao extends CrudRepository<UserRole, Long> {
	UserRole getById(long id);
	UserRole getByNameAndItSystem(String name, ItSystem itSystem);
	List<UserRole> findAll();
	UserRole getByIdentifier(String identifier);
	List<UserRole> findByItSystem(ItSystem itSystem);
	<S extends UserRole> S save(S entity);
	UserRole getByItSystemAndIdentifier(ItSystem itSystem, String identifier);
	List<UserRole> getByDelegatedFromCvrNotNull();
	int countBySystemRoleAssignmentsSystemRole(SystemRole systemRole);
}
