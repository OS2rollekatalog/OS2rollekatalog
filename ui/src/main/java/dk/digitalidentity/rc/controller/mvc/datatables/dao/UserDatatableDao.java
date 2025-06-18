package dk.digitalidentity.rc.controller.mvc.datatables.dao;

import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.User;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.datatables.repository.DataTablesRepository;
import org.springframework.data.jpa.domain.Specification;

import java.util.Collection;

public interface UserDatatableDao extends DataTablesRepository<User, String> {
	public static Specification<User> requesterPositionOrgUnitIn(Collection<OrgUnit> orgUnits) {
		return (root, query, builder) -> {
			query.distinct(true);

			Join<User, Position> positionJoin = root.join("positions", JoinType.INNER);

			Predicate orgUnitPredicate = positionJoin.get("orgUnit").in(orgUnits);
			Predicate notDeletedPredicate = builder.isFalse(root.get("deleted"));

			return builder.and(orgUnitPredicate, notDeletedPredicate);
		};
	}
}
