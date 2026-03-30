package dk.digitalidentity.rc.controller.mvc.datatables.dao;

import dk.digitalidentity.rc.controller.mvc.datatables.dao.model.CombinedRoleView;
import dk.digitalidentity.rc.rolerequest.model.enums.RequestableBy;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.datatables.repository.DataTablesRepository;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
public interface CombinedRoleViewDatatableDao extends DataTablesRepository<CombinedRoleView, Long> {

	static Specification<CombinedRoleView> isNotReadOnly() {
		return (root, _, cb) -> cb.equal(root.get("readOnly"), false);
	}

	static Specification<CombinedRoleView> orgUnitFilterMatchesOrEmpty(List<String> ancestorOuUuids) {
		return (root, _, cb) -> {
			// Check role filter: no filter OR filter matches
			Predicate noRoleFilter = cb.isNull(root.get("orgUnitFilterUuids"));

			List<Predicate> roleFilterMatches = ancestorOuUuids.stream()
				.map(uuid -> cb.like(
					cb.concat(cb.concat(",", root.get("orgUnitFilterUuids")), ","),
					"%," + uuid + ",%"
				))
				.collect(Collectors.toList());

			Predicate roleHasMatchingFilter = roleFilterMatches.isEmpty()
				? cb.disjunction()
				: cb.or(roleFilterMatches.toArray(new Predicate[0]));

			Predicate roleFilterOk = cb.or(noRoleFilter, roleHasMatchingFilter);

			// Check itSystem filter: no filter OR filter matches
			Predicate noItSystemFilter = cb.isNull(root.get("itSystemOrgUnitFilterUuids"));

			List<Predicate> itSystemFilterMatches = ancestorOuUuids.stream()
				.map(uuid -> cb.like(
					cb.concat(cb.concat(",", root.get("itSystemOrgUnitFilterUuids")), ","),
					"%," + uuid + ",%"
				))
				.collect(Collectors.toList());

			Predicate itSystemHasMatchingFilter = itSystemFilterMatches.isEmpty()
				? cb.disjunction()
				: cb.or(itSystemFilterMatches.toArray(new Predicate[0]));

			Predicate itSystemFilterOk = cb.or(noItSystemFilter, itSystemHasMatchingFilter);

			// Both must be satisfied: roleFilterOk AND itSystemFilterOk
			return cb.and(roleFilterOk, itSystemFilterOk);
		};
	}

	static Specification<CombinedRoleView> requesterPermissionIn(Collection<RequestableBy> permittedSettings) {
		return (root, _, cb) -> {
			Predicate[] predicates = permittedSettings.stream()
				.map(setting -> cb.like(
					cb.concat(cb.concat(",", root.get("effectiveRequesterPermission")), ","),
					"%," + setting.toString() + ",%"
				))
				.toArray(Predicate[]::new);
			return cb.or(predicates);
		};
	}

	static Specification<CombinedRoleView> excludeUserRolesById(Set<Long> userRoleIds) {
		return (root, _, cb) -> {
			if (userRoleIds == null || userRoleIds.isEmpty()) {
				return cb.conjunction();
			}

			return cb.or(
				cb.notEqual(root.get("type"), "userRole"),
				cb.not(root.get("id").in(userRoleIds))
			);
		};
	}

	static Specification<CombinedRoleView> excludeAlreadyAssigned(List<Long> assignedUserRoleIds, List<Long> assignedRoleGroupIds) {
		return (root, _, cb) -> {
			Predicate notUserRole = cb.not(
				cb.and(
					cb.equal(root.get("type"), "userRole"),
					root.get("id").in(assignedUserRoleIds.isEmpty() ? java.util.Collections.singletonList(-1L) : assignedUserRoleIds)
				)
			);

			Predicate notRoleGroup = cb.not(
				cb.and(
					cb.equal(root.get("type"), "roleGroup"),
					root.get("id").in(assignedRoleGroupIds.isEmpty() ? java.util.Collections.singletonList(-1L) : assignedRoleGroupIds)
				)
			);

			return cb.and(notUserRole, notRoleGroup);
		};
	}
}
