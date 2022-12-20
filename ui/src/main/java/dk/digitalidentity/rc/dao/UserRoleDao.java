package dk.digitalidentity.rc.dao;

import java.util.List;

import org.springframework.data.repository.CrudRepository;

import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.SystemRole;
import dk.digitalidentity.rc.dao.model.UserRole;

public interface UserRoleDao extends CrudRepository<UserRole, Long> {
	UserRole findById(long id);
	UserRole getByNameAndItSystem(String name, ItSystem itSystem);
	List<UserRole> findAll();
	List<UserRole> findByCanRequestTrue();
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
}
