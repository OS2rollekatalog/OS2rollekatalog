package dk.digitalidentity.rc.controller.mvc.datatables.dao;

import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.UserRole;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.datatables.repository.DataTablesRepository;
import org.springframework.data.jpa.domain.Specification;

public interface UserroleDatatableDao extends DataTablesRepository<UserRole, String> {

	public static Specification<UserRole> itSystemNotDeleted() {
		return (root, query, builder) -> {
			Root<UserRole> userrole = builder.treat(root, UserRole.class);
			Join<UserRole, ItSystem> userroleItsystem = root.join("itSystem", JoinType.LEFT);
			return builder.not(userroleItsystem.get("deleted"));
		};
	}

	public static Specification<UserRole> notAllowPostponing() {
		return (root, query, builder) -> {
			Root<UserRole> userrole = builder.treat(root, UserRole.class);
			return builder.not(userrole.get("allowPostponing"));
		};
	}
}
