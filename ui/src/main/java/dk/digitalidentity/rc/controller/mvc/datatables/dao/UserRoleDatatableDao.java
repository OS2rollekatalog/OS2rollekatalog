package dk.digitalidentity.rc.controller.mvc.datatables.dao;

import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.rolerequest.model.enums.RequesterOption;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.datatables.repository.DataTablesRepository;
import org.springframework.data.jpa.domain.Specification;

import java.util.Collection;

public interface UserRoleDatatableDao extends DataTablesRepository<UserRole, Long> {
	static Specification<UserRole> requesterPermissionIn(Collection<RequesterOption> requesterPermissions) {
		return (root, query, builder) -> {
			Root<UserRole> userrole = builder.treat(root, UserRole.class);
			return builder.isTrue(userrole.get("requesterPermission").in(requesterPermissions));
		};
	}

	static Specification<UserRole> itSystemRequesterPermissionIn(Collection<RequesterOption> requesterPermissions) {
		return (root, query, builder) -> {
			Root<UserRole> userrole = builder.treat(root, UserRole.class);
			Join<UserRole, ItSystem> userRoleItsystem = userrole.join("itSystem");
			return builder.isTrue(userRoleItsystem.get("requesterPermission").in(requesterPermissions));
		};
	}

	static Specification<UserRole> itSystemRequesterPermissionNull() {
		return (root, query, builder) -> {
			Root<UserRole> userrole = builder.treat(root, UserRole.class);
			Join<UserRole, ItSystem> userRoleItsystem = userrole.join("itSystem");
			return builder.isNull(userRoleItsystem.get("requesterPermission"));
		};
	}

	static Specification<UserRole> itSystemNotDeleted() {
		return (root, query, builder) -> {
			Root<UserRole> userrole = builder.treat(root, UserRole.class);
			Join<UserRole, ItSystem> userroleItsystem = root.join("itSystem", JoinType.LEFT);
			return builder.not(userroleItsystem.get("deleted"));
		};
	}

	static Specification<UserRole> notAllowPostponing() {
		return (root, query, builder) -> {
			Root<UserRole> userrole = builder.treat(root, UserRole.class);
			return builder.not(userrole.get("allowPostponing"));
		};
	}
}
