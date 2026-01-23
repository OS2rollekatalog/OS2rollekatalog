package dk.digitalidentity.rc.service.util;

import dk.digitalidentity.rc.dao.model.SystemRole;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Slf4j
public class FilterMatcher {
	private static final long REGEX_TIMEOUT_MS = 100;
	private static final ExecutorService executor = Executors.newCachedThreadPool();

	/**
	 * Check if a SystemRole matches any filter in the filter map
	 *
	 * @param systemRole The SystemRole to check
	 * @param filterMap List of filter strings in format "groupPattern;attributeName;filterValue"
	 * @return true if the SystemRole matches at least one filter, false otherwise
	 */
	public static boolean systemRoleMatchesGroupFilter(SystemRole systemRole, List<String> filterMap) {
		if (filterMap == null || filterMap.isEmpty()) {
			log.debug("No filters configured - including all SystemRoles");
			return true; // No filters = include all
		}

		String systemRoleName = systemRole.getName();
		if (systemRoleName == null || systemRoleName.trim().isEmpty()) {
			log.warn("SystemRole has no name - excluding from filter match");
			return false;
		}

		log.debug("Checking if SystemRole '{}' matches any of {} filters", systemRoleName, filterMap.size());

		for (String filterString : filterMap) {
			FilterEntry filter = parseFilterEntry(filterString);
			if (filter == null) {
				continue; // Skip invalid filter entries
			}

			// Check if the group pattern matches the SystemRole name
			if (matchesPattern(systemRoleName, filter.groupPattern) || matchesPattern(systemRole.getIdentifier(), filter.groupPattern)) {
				log.debug("SystemRole '{}' matches filter pattern '{}'", systemRoleName, filter.groupPattern);
				return true;
			}
		}

		log.debug("SystemRole '{}' does not match any filters - excluding", systemRoleName);
		return false;
	}

	/**
	 * Parse a filter entry string into a FilterEntry object
	 */
	private record FilterEntry (String groupPattern, String attributeName, String filterValue) {}
	private static FilterEntry parseFilterEntry(String filterString) {
		if (filterString == null || filterString.trim().isEmpty()) {
			log.warn("Empty filter string encountered");
			return null;
		}

		String[] parts = filterString.split(";");
		if (parts.length != 3) {
			log.warn("Invalid filter format (expected: groupPattern;attributeName;filterValue): {}", filterString);
			return null;
		}

		String groupPattern = parts[0].trim();
		String attributeName = parts[1].trim();
		String filterValue = parts[2].trim();

		if (groupPattern.isEmpty()) {
			log.warn("Empty group pattern in filter: {}", filterString);
			return null;
		}

		return new FilterEntry(groupPattern, attributeName, filterValue);
	}

	/**
	 * Check if a value matches a pattern (supports wildcards and regex)
	 */
	private static boolean matchesPattern(String value, String pattern) {
		if (value == null || value.isEmpty()) {
			return false;
		}

		if (pattern == null || pattern.isEmpty()) {
			return false;
		}

		try {
			// Check exact match first (case-insensitive)
			if (value.equalsIgnoreCase(pattern)) {
				return true;
			}

			// Check if it's a regex pattern
			if (isRegexPattern(pattern)) {
				return matchesRegexWithTimeout(value, pattern);
			}

			// Fall back to simple wildcard matching
			if (pattern.contains("*")) {
				return matchesWildcard(value, pattern);
			}

			return false;
		} catch (Exception e) {
			log.error("Error matching value '{}' against pattern '{}': {}", value, pattern, e.getMessage());
			return false;
		}
	}

	/**
	 * Check if a pattern is a regex pattern (contains regex special characters)
	 */
	private static boolean isRegexPattern(String pattern) {
		// Check for regex special characters
		String regexChars = "^$[](){}+?|\\";
		for (char c : regexChars.toCharArray()) {
			if (pattern.indexOf(c) >= 0) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Match a value against a regex pattern with timeout protection
	 */
	private static boolean matchesRegexWithTimeout(String value, String pattern) {
		try {
			Pattern compiledPattern = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);

			// Use Future with timeout to prevent ReDoS attacks
			Callable<Boolean> matchTask = () -> compiledPattern.matcher(value).matches();
			Future<Boolean> future = executor.submit(matchTask);

			try {
				return future.get(REGEX_TIMEOUT_MS, TimeUnit.MILLISECONDS);
			} catch (TimeoutException e) {
				future.cancel(true);
				log.warn("Regex matching timed out for pattern '{}' against value '{}' - treating as no match",
						pattern, value);
				return false;
			}
		} catch (PatternSyntaxException e) {
			log.warn("Invalid regex pattern '{}': {}", pattern, e.getMessage());
			return false;
		} catch (Exception e) {
			log.error("Error in regex matching for pattern '{}': {}", pattern, e.getMessage());
			return false;
		}
	}

	/**
	 * Match a value against a simple wildcard pattern (* only)
	 */
	private static boolean matchesWildcard(String value, String pattern) {
		String lowerValue = value.toLowerCase();
		String lowerPattern = pattern.toLowerCase();

		// Special case: pattern is just "*" - matches everything
		if (lowerPattern.equals("*")) {
			return true;
		}

		// Pattern: *middle*
		if (lowerPattern.startsWith("*") && lowerPattern.endsWith("*") && lowerPattern.length() > 2) {
			String middle = lowerPattern.substring(1, lowerPattern.length() - 1);
			return lowerValue.contains(middle);
		}

		// Pattern: *suffix
		if (lowerPattern.startsWith("*") && !lowerPattern.endsWith("*")) {
			String suffix = lowerPattern.substring(1);
			return lowerValue.endsWith(suffix);
		}

		// Pattern: prefix*
		if (lowerPattern.endsWith("*") && !lowerPattern.startsWith("*")) {
			String prefix = lowerPattern.substring(0, lowerPattern.length() - 1);
			return lowerValue.startsWith(prefix);
		}

		return false;
	}
}
