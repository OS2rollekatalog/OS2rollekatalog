package dk.digitalidentity.rc.controller.rest.orgunit.assign;

import java.util.List;

/**
 * The submitted assignment was well-formed but failed its business rules.
 */
public class AssignmentValidationException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	private final List<String> errors;

	public AssignmentValidationException(List<String> errors) {
		super(null, null, false, false);  // control flow, not a fault - no stack trace needed
		this.errors = errors;
	}

	public AssignmentValidationException(String error) {
		this(List.of(error));
	}

	public List<String> errors() {
		return errors;
	}
}
