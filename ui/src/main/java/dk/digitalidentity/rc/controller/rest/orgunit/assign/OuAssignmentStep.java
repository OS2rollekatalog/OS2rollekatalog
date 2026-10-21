package dk.digitalidentity.rc.controller.rest.orgunit.assign;

import java.util.Arrays;
import java.util.Optional;

public enum OuAssignmentStep {
	SELECT_SCOPE,
	EXCEPTED_OUS,
	EXCEPTED_USERS,
	TITLES,
	FUNCTIONS,
	MANAGER,
	RECEIPT;

	public String messageKey() {
		return "html.page.ou.assign.step." + name();
	}

	public static Optional<OuAssignmentStep> fromId(String id) {
		return Arrays.stream(values())
				.filter(step -> step.name().equals(id))
				.findAny();
	}
}
