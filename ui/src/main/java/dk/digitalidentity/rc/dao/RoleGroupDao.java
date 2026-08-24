package dk.digitalidentity.rc.dao;

import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.UserRole;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RoleGroupDao extends CrudRepository<RoleGroup, Long> {

	Optional<RoleGroup> findByName(String name);

	List<RoleGroup> findAll();

	List<RoleGroup> findByUserRoleAssignmentsUserRole(UserRole userRole);

	// requester_permission/approver_permission er @Convert(List<enum> -> CSV);
	// vi bruger MySQL/MariaDB's FIND_IN_SET, der er bygget til netop CSV-kolonner.
	@Query(nativeQuery = true, value = "SELECT * FROM rolegroup WHERE FIND_IN_SET(:permission, requester_permission) > 0")
	List<RoleGroup> findByRequesterPermissionContaining(@Param("permission") String permission);

	@Query(nativeQuery = true, value = "SELECT * FROM rolegroup WHERE FIND_IN_SET(:permission, approver_permission) > 0")
	List<RoleGroup> findByApproverPermissionContaining(@Param("permission") String permission);
}
