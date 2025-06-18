package dk.digitalidentity.rc.controller.mvc.datatables.dao;

import dk.digitalidentity.rc.controller.mvc.datatables.dao.model.UserRoleForRoleGroupId;
import dk.digitalidentity.rc.controller.mvc.datatables.dao.model.UserRoleForRoleGroupView;
import org.springframework.data.jpa.datatables.repository.DataTablesRepository;
import org.springframework.data.jpa.domain.Specification;

public interface UserRoleForRoleGroupViewDao  extends DataTablesRepository<UserRoleForRoleGroupView, UserRoleForRoleGroupId> {
	static Specification<UserRoleForRoleGroupView> hasRoleGroupId(Long roleGroupId) {
		return (root, query, builder) ->
				builder.equal(root.get("compositeKey").get("rolegroupId"), roleGroupId);
	}
}
