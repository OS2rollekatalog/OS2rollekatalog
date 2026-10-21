package dk.digitalidentity.rc.controller.rest.orgunit.assign;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The authoritative set of assignment scopes the wizard allows, as data.
 * A (target, exception) pair absent from {@link #RULES} is invalid.
 */
public final class ScopeRules {

	private ScopeRules() {
	}

	public enum AssignmentTarget {
		EVERYONE, TITLES, MANAGER, FUNCTION
	}

	public enum AssignmentException {
		NONE, TITLES, OUS, USERS
	}

	public record ScopeChoice(AssignmentTarget target, AssignmentException exception, boolean inherit) {
	}

	/** Whether a rule permits the user to choose inheritance, or fixes it. */
	public enum InheritConstraint {
		FREE, ALWAYS, NEVER;

		public boolean permits(boolean inherit) {
			return switch (this) {
				case FREE -> true;
				case ALWAYS -> inherit;
				case NEVER -> !inherit;
			};
		}

		public boolean appliedTo(boolean inherit) {
			return switch (this) {
				case FREE -> inherit;
				case ALWAYS -> true;
				case NEVER -> false;
			};
		}
	}

	public record ScopeRule(
			AssignmentTarget target,
			AssignmentException exception,
			InheritConstraint inherit,
			List<OuAssignmentStep> steps) {
	}

	private static final List<ScopeRule> RULES = List.of(
		new ScopeRule(AssignmentTarget.EVERYONE, AssignmentException.NONE, InheritConstraint.FREE, List.of()),
		new ScopeRule(AssignmentTarget.EVERYONE, AssignmentException.TITLES, InheritConstraint.FREE, List.of(OuAssignmentStep.TITLES)),
		new ScopeRule(AssignmentTarget.EVERYONE, AssignmentException.OUS, InheritConstraint.ALWAYS, List.of(OuAssignmentStep.EXCEPTED_OUS)),
		new ScopeRule(AssignmentTarget.EVERYONE, AssignmentException.USERS, InheritConstraint.NEVER, List.of(OuAssignmentStep.EXCEPTED_USERS)),
		new ScopeRule(AssignmentTarget.TITLES, AssignmentException.NONE, InheritConstraint.FREE, List.of(OuAssignmentStep.TITLES)),
		new ScopeRule(AssignmentTarget.TITLES, AssignmentException.USERS, InheritConstraint.NEVER, List.of(OuAssignmentStep.TITLES, OuAssignmentStep.EXCEPTED_USERS)),
		new ScopeRule(AssignmentTarget.MANAGER, AssignmentException.NONE, InheritConstraint.FREE, List.of(OuAssignmentStep.MANAGER)),
		new ScopeRule(AssignmentTarget.FUNCTION, AssignmentException.NONE, InheritConstraint.FREE, List.of(OuAssignmentStep.FUNCTIONS))
	);

	/** The rule for a choice, when the combination is allowed and its inherit answer is permitted. */
	public static Optional<ScopeRule> ruleFor(ScopeChoice choice) {
		return Optional.ofNullable(choice)
			.flatMap(valid -> RULES.stream()
				.filter(rule -> rule.target() == valid.target() && rule.exception() == valid.exception())
				.findFirst())
			.filter(rule -> rule.inherit().permits(choice.inherit()));
	}

	/**
	 * @return the choice if there is a rule for it, otherwise empty
	 */
	public static Optional<ScopeChoice> validated(ScopeChoice choice) {
		return ruleFor(choice).map(_ -> choice);
	}

	/**
	 * @return the wizard step ids required after selectScope for this choice, in order,
	 * or empty for an invalid combination
	 */
	public static Optional<List<OuAssignmentStep>> stepsFor(ScopeChoice choice) {
		return ruleFor(choice).map(ScopeRule::steps);
	}

	/**
	 * The TitleMode implied by a (target, exception) choice - target=TITLES and
	 * exception=TITLES are mutually exclusive, so at most one ever applies.
	 */
	public static Optional<TitleMode> titleModeFor(ScopeChoice choice) {
		return validated(choice).flatMap(valid -> {
			if (valid.target() == AssignmentTarget.TITLES) {
				return Optional.of(TitleMode.ONLY_SELECTED);
			}
			if (valid.exception() == AssignmentException.TITLES) {
				return Optional.of(TitleMode.EXCEPT_SELECTED);
			}
			return Optional.empty();
		});
	}

	/** The allowed exceptions per target with their inherit constraints, for the client's radio enablement. */
	public static Map<AssignmentTarget, Map<AssignmentException, InheritConstraint>> constraintsByTarget() {
		return RULES.stream()
			.collect(Collectors.groupingBy(ScopeRule::target,
				Collectors.toUnmodifiableMap(ScopeRule::exception, ScopeRule::inherit)));
	}

	/** Every rule, for callers that need to search by resulting shape rather than by choice. */
	public static List<ScopeRule> all() {
		return RULES;
	}
}
