package dk.digitalidentity.rc.dao;

import dk.digitalidentity.rc.dao.model.SystemRolePermission;
import dk.digitalidentity.rc.security.permission.Permission;
import dk.digitalidentity.rc.security.permission.Section;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Set;

public interface SystemRolePermissionDao extends JpaRepository<SystemRolePermission, Long> {
	boolean existsByEntityAndPermissionAndRoleIdentifierIn(Section entity, Permission permission, Collection<String> roleIdentifiers);

	Set<SystemRolePermission> findAllByEntityAndRoleIdentifierIn(Section entity, Collection<String> roleIdentifiers);
}
