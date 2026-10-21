/**
 * Accumulates the data collected across the wizard steps, shaped to match
 * OrgUnitAssignmentRestController.OrgUnitAssignmentRequest.
 */
export class AssignmentSelection {
	scopeChoice = null;
	scopeFields = {};
	startDate = null;
	stopDate = null;
	caseNumber = null;

	/**
	 * Scope fields are only meaningful for the choice they were answered under, so they are reset.
	 * @param {{target: string, exception: string, inherit: boolean}} scopeChoice
	 */
	setScopeChoice(scopeChoice) {
		this.scopeChoice = scopeChoice;
		this.scopeFields = {};
	}

	/**
	 * Merges a step's own fields into the flat bag; the server reads only what the
	 * resolved scope type needs and ignores the rest.
	 * @param {Object} partial
	 */
	updateScopeFields(partial) {
		this.scopeFields = {...this.scopeFields, ...partial};
	}

	/**
	 * @param {{startDate: ?string, stopDate: ?string, caseNumber: ?string}} fields
	 */
	setCommonFields({startDate, stopDate, caseNumber}) {
		this.startDate = startDate;
		this.stopDate = stopDate;
		this.caseNumber = caseNumber;
	}

	/** @returns {Object} the OrgUnitAssignmentRequest JSON payload */
	toRequestPayload() {
		return {
			scopeChoice: this.scopeChoice,
			scopeFields: this.scopeFields,
			startDate: this.startDate,
			stopDate: this.stopDate,
			caseNumber: this.caseNumber,
		};
	}
}
