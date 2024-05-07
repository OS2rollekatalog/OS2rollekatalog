package dk.digitalidentity.rc.dao;

import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.SystemRole;
import dk.digitalidentity.rc.dao.model.UserRole;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface UserRoleDao extends CrudRepository<UserRole, Long> {
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
