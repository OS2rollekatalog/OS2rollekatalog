package dk.digitalidentity.rc.rolerequest.model.permission;

import dk.digitalidentity.rc.rolerequest.model.enums.ApprovableBy;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Who may approve a request for a role: {@code INHERIT}, {@code AUTOMATIC}, nobody (only admins), or a set of
 * the remaining {@link ApprovableBy} values. The exclusive values can never be combined with others.
 * The rules for resolving and matching a permission live in the database views and {@code ApproverRule}.
 */
public final class ApproverPermission {
	private static final ApproverPermission INHERIT = new ApproverPermission(EnumSet.of(ApprovableBy.INHERIT));
	private static final ApproverPermission AUTOMATIC = new ApproverPermission(EnumSet.of(ApprovableBy.AUTOMATIC));
	private static final ApproverPermission NOBODY = new ApproverPermission(EnumSet.noneOf(ApprovableBy.class));

	private final List<ApprovableBy> approvableBy;

	private ApproverPermission(EnumSet<ApprovableBy> approvableBy) {
		this.approvableBy = List.copyOf(approvableBy);
	}

	public static ApproverPermission inherit() {
		return INHERIT;
	}

	public static ApproverPermission automatic() {
		return AUTOMATIC;
	}

	public static ApproverPermission nobody() {
		return NOBODY;
	}

	/**
	 * An empty collection means nobody, so only admins can approve.
	 * @throws IllegalArgumentException if INHERIT or AUTOMATIC is combined with other values
	 */
	public static ApproverPermission of(Collection<ApprovableBy> values) {
		if (values == null || values.isEmpty()) {
			return NOBODY;
		}
		EnumSet<ApprovableBy> set = EnumSet.copyOf(values);
		if (set.size() > 1 && (set.contains(ApprovableBy.INHERIT) || set.contains(ApprovableBy.AUTOMATIC))) {
			throw new IllegalArgumentException("INHERIT and AUTOMATIC cannot be combined with other values: " + set);
		}
		return canonical(set);
	}

	/**
	 * @throws IllegalArgumentException if the values are INHERIT or an invalid combination
	 */
	public static ApproverPermission global(Collection<ApprovableBy> values) {
		ApproverPermission permission = of(values);
		if (permission.isInherit()) {
			throw new IllegalArgumentException("The global approver setting cannot be INHERIT");
		}
		return permission;
	}

	public static Set<ApprovableBy> parseValues(String text) {
		return Arrays.stream(text.split(","))
			.map(String::trim)
			.filter(s -> !s.isEmpty())
			.map(ApprovableBy::valueOf)
			.collect(Collectors.toCollection(() -> EnumSet.noneOf(ApprovableBy.class)));
	}

	/**
	 * Reduces an invalid combination to its exclusive value: INHERIT wins, then AUTOMATIC.
	 */
	public static Set<ApprovableBy> resolveConflicts(Set<ApprovableBy> values) {
		if (values.contains(ApprovableBy.INHERIT)) {
			return EnumSet.of(ApprovableBy.INHERIT);
		}
		if (values.contains(ApprovableBy.AUTOMATIC)) {
			return EnumSet.of(ApprovableBy.AUTOMATIC);
		}
		return values;
	}

	private static ApproverPermission canonical(EnumSet<ApprovableBy> set) {
		if (set.contains(ApprovableBy.INHERIT)) {
			return INHERIT;
		}
		if (set.contains(ApprovableBy.AUTOMATIC)) {
			return AUTOMATIC;
		}
		return new ApproverPermission(set);
	}

	public boolean isInherit() {
		return this == INHERIT;
	}

	public boolean isAutomatic() {
		return this == AUTOMATIC;
	}

	public boolean isNobody() {
		return approvableBy.isEmpty();
	}

	public List<ApprovableBy> getApprovableBy() {
		return approvableBy;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof ApproverPermission that && approvableBy.equals(that.approvableBy);
	}

	@Override
	public int hashCode() {
		return approvableBy.hashCode();
	}

	@Override
	public String toString() {
		return approvableBy.stream().map(Enum::name).collect(Collectors.joining(","));
	}
}
