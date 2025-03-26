package dk.digitalidentity.rc.controller.mvc.datatables.dao;

import dk.digitalidentity.rc.dao.model.User;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.datatables.repository.DataTablesRepository;
import org.springframework.data.jpa.domain.Specification;

public interface DatatablesUserDao extends DataTablesRepository<User, String> {

	public static Specification<User> isDeletedFalse() {
		return (root, query, builder) -> {
			Root<User> user = builder.treat(root, User.class);
			return builder.isFalse(user.get("deleted"));
		};
	}
}
