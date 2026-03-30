package dk.digitalidentity.rc.controller.mvc.datatables.dao;

import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.rolerequest.model.enums.RequestableBy;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.datatables.repository.DataTablesRepository;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public interface RolegroupDatatableDao extends DataTablesRepository<RoleGroup, Long> {
	 static Specification<RoleGroup> requesterPermissionIn(List<RequestableBy> permissions) {
		return (root, _, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			for (RequestableBy permission : permissions) {
				// Check if the comma-separated string contains this permission as the permission is now a comma list
				predicates.add(cb.like(root.get("requesterPermission"), "%" + permission.name() + "%"));
			}
			return cb.or(predicates.toArray(new Predicate[0]));
		};
	}

	static Specification<RoleGroup> requesterPermissionContains(RequestableBy permission) {
		return requesterPermissionIn(List.of(permission));
	}

	static Specification<RoleGroup> ouFilterMatches(List<String> ancestorOuUuids) {
		return (root, query, cb) -> {
			Predicate notEnabled = cb.equal(root.get("ouFilterEnabled"), false);
			Predicate emptyFilter = cb.isEmpty(root.get("orgUnitFilterOrgUnits"));
			var ouJoin = root.join("orgUnitFilterOrgUnits", jakarta.persistence.criteria.JoinType.LEFT);
			Predicate matchesAncestor = ouJoin.get("uuid").in(ancestorOuUuids);
			query.distinct(true);
			return cb.or(notEnabled, emptyFilter, matchesAncestor);
		};
	}

	/**
	 * Filter role groups with AUTHORIZED permission to only allow if user has access to ALL IT systems.
	 * Role groups without AUTHORIZED permission pass through unfiltered.
	 */
	static Specification<RoleGroup> authorizedRoleGroupsRequireAllItSystems(boolean hasAccessToAllItSystems) {
		return (root, query, cb) -> {
			if (hasAccessToAllItSystems) {
				// User has access to all IT systems - no filtering needed
				return cb.conjunction();
			}

			// User does NOT have access to all IT systems - exclude role groups with AUTHORIZED permission
			return cb.not(requesterPermissionContains(RequestableBy.AUTHORIZED).toPredicate(root, query, cb));
		};
	}

}
