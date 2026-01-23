package dk.digitalidentity.rc.dao.serializer;

import dk.digitalidentity.rc.dao.model.SystemRole;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignment;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.Set;

public interface SystemRoleAssignmentDao extends CrudRepository<SystemRoleAssignment, Long> {

	@Query(value = "SELECT DISTINCT sra.*\n" +
			"FROM system_role_assignments sra\n" +
			"         INNER JOIN system_roles sr ON sra.system_role_id = sr.id\n" +
			"         INNER JOIN it_systems its ON sr.it_system_id = its.id AND its.identifier = 'RoleCatalogue'\n" +
			"         INNER JOIN user_roles ur ON sra.user_role_id = ur.id\n" +
			"         LEFT JOIN user_roles_mapping urm ON ur.id = urm.role_id AND urm.user_uuid = :userUuid\n" +
			"         LEFT JOIN rolegroup_roles rgr ON ur.id = rgr.role_id\n" +
			"         LEFT JOIN user_rolegroups urg ON rgr.rolegroup_id = urg.rolegroup_id AND urg.user_uuid = :userUuid\n" +
			"WHERE urm.user_uuid IS NOT NULL OR urg.user_uuid IS NOT NULL", nativeQuery = true)
	Set<SystemRoleAssignment> findAllForUserInRolecatalogue(@Param("userUuid") String userUuid);

	Set<SystemRoleAssignment> findAllBySystemRole(SystemRole systemRole);
}
