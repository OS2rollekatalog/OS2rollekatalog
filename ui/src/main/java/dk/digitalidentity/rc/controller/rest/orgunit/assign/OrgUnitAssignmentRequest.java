package dk.digitalidentity.rc.controller.rest.orgunit.assign;

import java.time.LocalDate;

import dk.digitalidentity.rc.controller.rest.orgunit.assign.ScopeRules.ScopeChoice;

public record OrgUnitAssignmentRequest(
		ScopeChoice scopeChoice,
		ScopeFields scopeFields,
		LocalDate startDate,
		LocalDate stopDate,
		String caseNumber) {
}
