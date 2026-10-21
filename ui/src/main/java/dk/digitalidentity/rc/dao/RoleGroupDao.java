package dk.digitalidentity.rc.dao;

import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.UserRole;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface RoleGroupDao extends CrudRepository<RoleGroup, Long> {

	Optional<RoleGroup> findByName(String name);

	List<RoleGroup> findAll();

	List<RoleGroup> findByUserRoleAssignmentsUserRole(UserRole userRole);

	List<RoleGroup> findAllByIdIn(Collection<Long> ids);

	@Query("SELECT a.userRole.id FROM rolegroup_roles a WHERE a.roleGroup.id IN :roleGroupIds")
	Set<Long> findUserRoleIdsByRoleGroupIdIn(@Param("roleGroupIds") Collection<Long> roleGroupIds);

	<T> Optional<T> findById(Long id, Class<T> projectionType);
}
