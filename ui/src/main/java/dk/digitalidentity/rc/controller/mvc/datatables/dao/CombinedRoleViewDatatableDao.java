package dk.digitalidentity.rc.controller.mvc.datatables.dao;

import dk.digitalidentity.rc.controller.mvc.datatables.dao.model.CombinedRoleView;
import dk.digitalidentity.rc.controller.mvc.datatables.dao.model.CombinedRoleViewId;
import dk.digitalidentity.rc.dao.SpecificationIdFinder;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.datatables.repository.DataTablesRepository;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public interface CombinedRoleViewDatatableDao extends DataTablesRepository<CombinedRoleView, CombinedRoleViewId>, SpecificationIdFinder<CombinedRoleView> {

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
