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
import java.util.Collections;

public interface UserDatatableDao extends DataTablesRepository<User, String> {

	static Specification<User> notDeletedOrDisabled() {
		return (root, _, builder) ->
			builder.and(builder.isFalse(root.get("deleted")), builder.isFalse(root.get("disabled")));
	}

	static Specification<User> requesterPositionOrgUnitIn(Collection<OrgUnit> orgUnits) {
		return requesterPositionOrgUnitInOrUserIn(orgUnits, Collections.emptySet());
	}

	// Matches users with a position in one of the given OUs, plus users explicitly listed by uuid
	// (e.g. managers of sub-OUs, who have their position in the OU they themselves manage).
	static Specification<User> requesterPositionOrgUnitInOrUserIn(Collection<OrgUnit> orgUnits, Collection<String> userUuids) {
		return (root, query, builder) -> {
			query.distinct(true);

			Join<User, Position> positionJoin = root.join("positions", JoinType.LEFT);
			Predicate scopePredicate = positionJoin.get("orgUnit").in(orgUnits);
			if (!userUuids.isEmpty()) {
				scopePredicate = builder.or(scopePredicate, root.get("uuid").in(userUuids));
			}

			return builder.and(scopePredicate, notDeletedOrDisabled().toPredicate(root, query, builder));
		};
	}
}
