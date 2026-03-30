package dk.digitalidentity.rc.controller.mvc.datatables.dao;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.jpa.datatables.repository.DataTablesRepository;
import org.springframework.data.jpa.domain.Specification;

import dk.digitalidentity.rc.controller.mvc.datatables.dao.model.UserRoleView;
import dk.digitalidentity.rc.rolerequest.model.enums.RequestableBy;
import jakarta.persistence.criteria.Predicate;

public interface UserRoleViewDatatableDao extends DataTablesRepository<UserRoleView, Long> {
	 static Specification<UserRoleView> requesterPermissionIn(List<RequestableBy> permissions) {
		return (root, _, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			for (RequestableBy permission : permissions) {
				// Check if the comma-separated string contains this permission as the permission is now a comma list
				predicates.add(cb.like(root.get("effectiveRequesterPermission"), "%" + permission.name() + "%"));
			}
			return cb.or(predicates.toArray(new Predicate[0]));
		};
	}

	static Specification<UserRoleView> requesterPermissionContains(RequestableBy permission) {
		return requesterPermissionIn(List.of(permission));
	}

	static Specification<UserRoleView> isNotReadOnly() {
		return (root, _, cb) -> cb.not(root.get("readOnly"));
	}

	/**
	 * Filter roles with AUTHORIZED permission to only those with accessible IT systems.
	 * Roles without AUTHORIZED permission pass through unfiltered.
	 */
	static Specification<UserRoleView> authorizedRolesLimitedToItSystems(Set<Long> accessibleItSystemIds) {
		return (root, query, cb) -> {
			if (accessibleItSystemIds == null || accessibleItSystemIds.isEmpty()) {
				// No accessible IT systems - only allow roles WITHOUT AUTHORIZED permission
				return cb.not(requesterPermissionContains(RequestableBy.AUTHORIZED).toPredicate(root, query, cb));
			}

			// Roles either don't have AUTHORIZED, OR have AUTHORIZED and accessible ITSystem
			Predicate notAuthorized = cb.not(requesterPermissionContains(RequestableBy.AUTHORIZED).toPredicate(root, query, cb));
			Predicate isAuthorizedAndAccessible = cb.and(
				requesterPermissionContains(RequestableBy.AUTHORIZED).toPredicate(root, query, cb),
				root.get("itSystemId").in(accessibleItSystemIds)
			);

			return cb.or(notAuthorized, isAuthorizedAndAccessible);
		};
	}

	static Specification<UserRoleView> authorizedRolesLimitedToOrgUnits(String positionOUUuid, Set<String> orgUnitUuids) {
		if (positionOUUuid == null) {
			throw new RuntimeException("positionOUUuid cannot be null");
		}
		return (root, query, cb) -> {
			// Hvis brugerens position IKKE er i de tilladte orgUnits
			if (orgUnitUuids.isEmpty() || !orgUnitUuids.contains(positionOUUuid)) {
				// Blokér alle roller med AUTHORIZED permission
				return cb.not(requesterPermissionContains(RequestableBy.AUTHORIZED).toPredicate(root, query, cb));
			}
			// Brugerens position ER i orgUnitUuids - tillad AUTHORIZED roller
			return cb.conjunction(); // Matcher alt
		};
	}

	/**
	 * Filter roles to only those that either have no orgUnit filter, or have the specified orgUnit in their filter list
	 */
	public static Specification<UserRoleView> orgUnitFilterMatchesOrEmpty(List<String> ancestorOuUuids) {
		return (root, _, criteriaBuilder) -> {
			// Check role filter: no filter OR filter matches
			Predicate noRoleFilter = criteriaBuilder.or(
				criteriaBuilder.isNull(root.get("orgUnitFilterUuids")),
				criteriaBuilder.equal(root.get("orgUnitFilterUuids"), "")
			);

			List<Predicate> roleFilterMatches = ancestorOuUuids.stream()
				.map(uuid -> criteriaBuilder.like(
					criteriaBuilder.concat(criteriaBuilder.concat(",", root.get("orgUnitFilterUuids")), ","),
					"%," + uuid + ",%"
				))
				.collect(Collectors.toList());

			Predicate roleHasMatchingFilter = roleFilterMatches.isEmpty()
				? criteriaBuilder.disjunction()
				: criteriaBuilder.or(roleFilterMatches.toArray(new Predicate[0]));

			Predicate roleFilterOk = criteriaBuilder.or(noRoleFilter, roleHasMatchingFilter);

			// Check itSystem filter: no filter OR filter matches
			Predicate noItSystemFilter = criteriaBuilder.or(
				criteriaBuilder.isNull(root.get("itSystemOrgUnitFilterUuids")),
				criteriaBuilder.equal(root.get("itSystemOrgUnitFilterUuids"), "")
			);

			List<Predicate> itSystemFilterMatches = ancestorOuUuids.stream()
				.map(uuid -> criteriaBuilder.like(
					criteriaBuilder.concat(criteriaBuilder.concat(",", root.get("itSystemOrgUnitFilterUuids")), ","),
					"%," + uuid + ",%"
				))
				.collect(Collectors.toList());

			Predicate itSystemHasMatchingFilter = itSystemFilterMatches.isEmpty()
				? criteriaBuilder.disjunction()
				: criteriaBuilder.or(itSystemFilterMatches.toArray(new Predicate[0]));

			Predicate itSystemFilterOk = criteriaBuilder.or(noItSystemFilter, itSystemHasMatchingFilter);

			// Both must be satisfied: roleFilterOk AND itSystemFilterOk
			return criteriaBuilder.and(roleFilterOk, itSystemFilterOk);
		};
	}
}
