package dk.digitalidentity.rc.rolerequest.model.permission;

import dk.digitalidentity.rc.rolerequest.model.enums.RequestableBy;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Who may request a role: {@code INHERIT}, {@code NONE}, or a set of the remaining {@link RequestableBy} values.
 */
public final class RequesterPermission {
	private static final RequesterPermission INHERIT = new RequesterPermission(EnumSet.of(RequestableBy.INHERIT));
	private static final RequesterPermission NONE = new RequesterPermission(EnumSet.of(RequestableBy.NONE));

	private final List<RequestableBy> requestableBy;

	private RequesterPermission(EnumSet<RequestableBy> requestableBy) {
		this.requestableBy = List.copyOf(requestableBy);
	}

	public static RequesterPermission inherit() {
		return INHERIT;
	}

	public static RequesterPermission none() {
		return NONE;
	}

	/**
	 * An empty collection means INHERIT.
	 * @throws IllegalArgumentException if INHERIT or NONE is combined with other values
	 */
	public static RequesterPermission of(Collection<RequestableBy> values) {
		if (values == null || values.isEmpty()) {
			return INHERIT;
		}
		EnumSet<RequestableBy> set = EnumSet.copyOf(values);
		if (set.size() > 1 && (set.contains(RequestableBy.INHERIT) || set.contains(RequestableBy.NONE))) {
			throw new IllegalArgumentException("INHERIT and NONE cannot be combined with other values: " + set);
		}
		return canonical(set);
	}

	/**
	 * The global setting cannot inherit, and an empty collection means NONE.
	 * @throws IllegalArgumentException if the values are INHERIT or an invalid combination
	 */
	public static RequesterPermission global(Collection<RequestableBy> values) {
		if (values == null || values.isEmpty()) {
			return NONE;
		}
		RequesterPermission permission = of(values);
		if (permission.isInherit()) {
			throw new IllegalArgumentException("The global requester setting cannot be INHERIT");
		}
		return permission;
	}

	public static Set<RequestableBy> parseValues(String text) {
		return Arrays.stream(text.split(","))
			.map(String::trim)
			.filter(s -> !s.isEmpty())
			.map(RequestableBy::valueOf)
			.collect(Collectors.toCollection(() -> EnumSet.noneOf(RequestableBy.class)));
	}

	/**
	 * Reduces an invalid combination to its exclusive value: INHERIT wins, then NONE.
	 */
	public static Set<RequestableBy> resolveConflicts(Set<RequestableBy> values) {
		if (values.contains(RequestableBy.INHERIT)) {
			return EnumSet.of(RequestableBy.INHERIT);
		}
		if (values.contains(RequestableBy.NONE)) {
			return EnumSet.of(RequestableBy.NONE);
		}
		return values;
	}

	private static RequesterPermission canonical(EnumSet<RequestableBy> set) {
		if (set.contains(RequestableBy.INHERIT)) {
			return INHERIT;
		}
		if (set.contains(RequestableBy.NONE)) {
			return NONE;
		}
		return new RequesterPermission(set);
	}

	public boolean isInherit() {
		return this == INHERIT;
	}

	public boolean isNone() {
		return this == NONE;
	}

	public List<RequestableBy> getRequestableBy() {
		return requestableBy;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof RequesterPermission that && requestableBy.equals(that.requestableBy);
	}

	@Override
	public int hashCode() {
		return requestableBy.hashCode();
	}

	@Override
	public String toString() {
		return requestableBy.stream().map(Enum::name).collect(Collectors.joining(","));
	}
}
