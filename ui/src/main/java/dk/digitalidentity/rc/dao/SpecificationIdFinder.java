package dk.digitalidentity.rc.dao;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Set;
import java.util.stream.Collectors;

public interface SpecificationIdFinder<T> extends JpaSpecificationExecutor<T> {

	record EntityId(long id) {}

	/**
	 * Selects only the id column; prefer this over findAll(spec), which loads whole entities.
	 */
	default Set<Long> findIds(Specification<T> specification) {
		return findBy(specification, query -> query.as(EntityId.class).all()).stream()
			.map(EntityId::id)
			.collect(Collectors.toSet());
	}
}
