package dk.digitalidentity.rc.rolerequest.dao;

import dk.digitalidentity.rc.dao.SpecificationIdFinder.EntityId;
import dk.digitalidentity.rc.rolerequest.model.entity.RoleGroupPermissionView;
import dk.digitalidentity.rc.rolerequest.model.permission.ApproverPermission;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface RoleGroupPermissionViewDao extends Repository<RoleGroupPermissionView, Long>, JpaSpecificationExecutor<RoleGroupPermissionView> {

	Optional<RoleGroupPermissionView> findById(Long id);

	List<RoleGroupPermissionView> findByIdIn(Collection<Long> ids);

	Set<EntityId> findByEffectiveApproverPermission(ApproverPermission permission);
}
