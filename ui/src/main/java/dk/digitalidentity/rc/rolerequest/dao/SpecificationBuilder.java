package dk.digitalidentity.rc.rolerequest.dao;

import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class SpecificationBuilder<T> {
	private Specification<T> specification;

	private SpecificationBuilder(Specification<T> initial) {
		this.specification = initial;
	}

	public static <T> SpecificationBuilder<T> create(Class<T> entityClass) {
	    return new SpecificationBuilder<>(Specification.allOf());
	}

	public SpecificationBuilder<T> and(Specification<T> spec) {
		if (spec != null) {
			this.specification = this.specification.and(spec);
		}
		return this;
	}

	public SpecificationBuilder<T> andIf(boolean condition, Specification<T> spec) {
		if (condition && spec != null) {
			this.specification = this.specification.and(spec);
		}
		return this;
	}

	public SpecificationBuilder<T> or(Specification<T> spec) {
		if (spec != null) {
			this.specification = this.specification.or(spec);
		}
		return this;
	}

	/**
	 * AND med en gruppe af OR conditions
	 * Eksempel: .andOr(spec1, spec2, spec3) -> AND (spec1 OR spec2 OR spec3)
	 */
	public SpecificationBuilder<T> andOr(Specification<T>... specs) {
		if (specs == null || specs.length == 0) {
			return this;
		}

		Specification<T> orSpec = null;
		for (Specification<T> spec : specs) {
			if (spec != null) {
				orSpec = (orSpec == null) ? spec : orSpec.or(spec);
			}
		}

		if (orSpec != null) {
			this.specification = this.specification.and(orSpec);
		}
		return this;
	}

	/**
	 * Byg en OR gruppe conditionally
	 * Eksempel: .andOrBuilder(b -> b.orIf(cond1, spec1).orIf(cond2, spec2))
	 */
	public SpecificationBuilder<T> andOrGroup(OrGroupBuilder<T> builder) {
		OrGroup<T> group = new OrGroup<>();
		builder.build(group);

		Specification<T> orSpec = group.buildOr();
		if (orSpec != null) {
			this.specification = this.specification.and(orSpec);
		}
		return this;
	}

	public Specification<T> build() {
		return specification;
	}

	// Helper klasser
	public interface OrGroupBuilder<T> {
		void build(OrGroup<T> group);
	}

	public static class OrGroup<T> {
		private final List<Specification<T>> specs = new ArrayList<>();

		public OrGroup<T> or(Specification<T> spec) {
			if (spec != null) {
				specs.add(spec);
			}
			return this;
		}

		public OrGroup<T> orIf(boolean condition, Specification<T> spec) {
			if (condition && spec != null) {
				specs.add(spec);
			}
			return this;
		}

		Specification<T> buildOr() {
			if (specs.isEmpty()) {
				return null;
			}
			Specification<T> result = specs.get(0);
			for (int i = 1; i < specs.size(); i++) {
				result = result.or(specs.get(i));
			}
			return result;
		}
	}
}
