package dk.digitalidentity.rc.util;

import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/**
 * Shared logic for rendering postponed constraints as a human-readable string.
 * UUID-shaped values (organisation constraint values) are resolved to display names
 * via the caller-supplied resolver; any other value, or a UUID the resolver can't
 * resolve (e.g. trust functions, see #85), is shown as-is.
 */
public abstract class PostponedConstraintsFormatter {

	/**
	 * Formats a single constraint's values, e.g. "Organisation: <name1>, <name2>".
	 * Returns just the constraint name if there are no values.
	 */
	public static String formatConstraint(final String constraintName, final List<String> constraintValues, final UnaryOperator<String> uuidResolver) {
		if (constraintValues == null || constraintValues.isEmpty()) {
			return constraintName;
		}
		final String translatedValues = constraintValues.stream()
				.map(value -> resolveValue(value, uuidResolver))
				.collect(Collectors.joining(", "));
		return constraintName + ": " + translatedValues;
	}

	/**
	 * Formats a single constraint's values as one "Name: value" line per value,
	 * e.g. "Organisation: <name1>\nOrganisation: <name2>". Returns just the constraint
	 * name (no trailing newline) if there are no values.
	 */
	public static String formatConstraintOnePerLine(final String constraintName, final List<String> constraintValues, final UnaryOperator<String> uuidResolver) {
		if (constraintValues == null || constraintValues.isEmpty()) {
			return constraintName;
		}
		return constraintValues.stream()
				.map(value -> constraintName + ": " + resolveValue(value, uuidResolver))
				.collect(Collectors.joining("\n"));
	}

	/**
	 * Translates a stored postponed-constraints snapshot string (e.g. "Organisation: <uuid>,<uuid>\nKLE: 00.01")
	 * by replacing any UUID-shaped value with its resolved display name, so old snapshot rows
	 * display correctly without needing a data migration.
	 */
	public static String translate(final String postponedConstraints, final UnaryOperator<String> uuidResolver) {
		if (StringUtils.isBlank(postponedConstraints)) {
			return postponedConstraints;
		}
		return Arrays.stream(postponedConstraints.split("\n"))
				.map(line -> translateLine(line, uuidResolver))
				.collect(Collectors.joining("\n"));
	}

	private static String translateLine(final String line, final UnaryOperator<String> uuidResolver) {
		final int separatorIndex = line.indexOf(": ");
		if (separatorIndex < 0) {
			return line;
		}
		final String constraintName = line.substring(0, separatorIndex);
		final String values = line.substring(separatorIndex + 2);
		final List<String> constraintValues = Arrays.stream(StringUtils.split(values, ","))
				.map(String::trim)
				.collect(Collectors.toList());
		return formatConstraint(constraintName, constraintValues, uuidResolver);
	}

	private static String resolveValue(final String value, final UnaryOperator<String> uuidResolver) {
		final String trimmed = value.trim();
		if (!UuidUtil.isUuid(trimmed)) {
			return trimmed;
		}
		return uuidResolver.apply(trimmed);
	}

}
