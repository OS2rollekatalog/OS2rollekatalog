package dk.digitalidentity.rc.util;

import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.helpers.FormattingTuple;
import org.slf4j.helpers.MessageFormatter;
import org.springframework.security.access.AccessDeniedException;

import dk.digitalidentity.rc.exceptions.NotFoundException;
import dk.digitalidentity.rc.security.permission.NotPermittedException;
import dk.digitalidentity.rc.security.permission.Permission;
import dk.digitalidentity.rc.security.permission.Section;

/**
 * Convenience for logging correctly on throw, and steering towards the right exception type.
 */
public final class Failure {

	private Failure() {
	}

	// ---- Named shortcuts for common cases ---- //

	/**
	 * A lookup (e.g. by id) found nothing. Typically used with {@code Optional.orElseThrow(...)}.
	 */
	public static NotFoundException notFound(final Logger log, final String messagePattern, final Object... args) {
		return warn(log, NotFoundException::new, messagePattern, args);
	}

	/**
	 * An argument failed validation (missing, malformed, out of range).
	 */
	public static IllegalArgumentException illegalArgument(final Logger log, final String messagePattern, final Object... args) {
		return warn(log, IllegalArgumentException::new, messagePattern, args);
	}

	/**
	 * The actor is not allowed to perform the action, for a reason other than a Permission check
	 * (e.g. a business-rule or state conflict).
	 */
	public static AccessDeniedException accessDenied(final Logger log, final String messagePattern, final Object... args) {
		return warn(log, AccessDeniedException::new, messagePattern, args);
	}

	/**
	 * The actor lacks the required Permission for a Section of the app.
	 */
	public static NotPermittedException notPermitted(final Logger log, final String message, final Section entity, final Permission permission) {
		return notPermitted(log, message, entity, permission, false);
	}

	/**
	 * The actor lacks the required Permission for a Section of the app, or fails a constraint on that Permission.
	 */
	public static NotPermittedException notPermitted(final Logger log, final String message, final Section entity, final Permission permission, final boolean constraintMismatched) {
		log.warn("Access denied: permission {} on {} (constraintMismatched={})", permission, entity, constraintMismatched);
		return new NotPermittedException(message, entity, permission, constraintMismatched);
	}

	// ---- Generic fallback for any other exception type ---- //

	/**
	 * Logs a warn, then builds and returns the exception via constructor. One call, nothing deferred.
	 */
	public static <T extends Throwable> T warn(final Logger log, final Function<String, T> constructor, final String messagePattern, final Object... args) {
		return build(log, Level.WARN, constructor, messagePattern, args);
	}

	/**
	 * Logs an error, then builds and returns the exception via constructor. One call, nothing deferred.
	 */
	public static <T extends Throwable> T error(final Logger log, final Function<String, T> constructor, final String messagePattern, final Object... args) {
		return build(log, Level.ERROR, constructor, messagePattern, args);
	}

	private static <T extends Throwable> T build(final Logger log, final Level level, final Function<String, T> constructor, final String messagePattern, final Object[] args) {
		final FormattingTuple formatted = MessageFormatter.arrayFormat(messagePattern, args);
		final String message = formatted.getMessage();
		final Throwable cause = formatted.getThrowable();

		switch (level) {
			case WARN -> {
				if (cause != null) {
					log.warn(message, cause);
				}
				else {
					log.warn(message);
				}
			}
			case ERROR -> {
				if (cause != null) {
					log.error(message, cause);
				}
				else {
					log.error(message);
				}
			}
		}

		return constructor.apply(message);
	}

	private enum Level {
		WARN, ERROR
	}
}
