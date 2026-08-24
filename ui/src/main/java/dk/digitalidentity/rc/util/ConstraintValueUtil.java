package dk.digitalidentity.rc.util;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public final class ConstraintValueUtil {

	private ConstraintValueUtil() {}

	public static Set<String> parseFunctionUuids(String constraintValue) {
		if (constraintValue == null || constraintValue.isBlank()) {
			return Set.of();
		}
		return Arrays.stream(constraintValue.split(","))
			.map(String::trim)
			.filter(s -> !s.isEmpty())
			.collect(Collectors.toSet());
	}
}
