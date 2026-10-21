package dk.digitalidentity.rc.rolerequest.dao;

import dk.digitalidentity.rc.dao.SpecificationIdFinder;
import dk.digitalidentity.rc.rolerequest.model.entity.UserRolePermissionView;
import dk.digitalidentity.rc.rolerequest.model.permission.ApproverPermission;
import org.springframework.data.repository.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface UserRolePermissionViewDao extends Repository<UserRolePermissionView, Long>, SpecificationIdFinder<UserRolePermissionView> {

	Optional<UserRolePermissionView> findById(Long id);

	List<UserRolePermissionView> findByIdIn(Collection<Long> ids);

	Set<EntityId> findByEffectiveApproverPermission(ApproverPermission permission);
}
