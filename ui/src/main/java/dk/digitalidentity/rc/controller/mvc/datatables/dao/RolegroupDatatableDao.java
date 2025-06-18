package dk.digitalidentity.rc.controller.mvc.datatables.dao;

import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.rolerequest.model.enums.RequesterOption;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.datatables.repository.DataTablesRepository;
import org.springframework.data.jpa.domain.Specification;

import java.util.Collection;

public interface RolegroupDatatableDao extends DataTablesRepository<RoleGroup, Long> {
	static Specification<RoleGroup> requesterPermissionIn(Collection<RequesterOption> requesterPermissions) {
		return (root, query, builder) -> {
			Root<RoleGroup> rolegroup = builder.treat(root, RoleGroup.class);
			return builder.isTrue(rolegroup.get("requesterPermission").in(requesterPermissions));
		};
	}
}
