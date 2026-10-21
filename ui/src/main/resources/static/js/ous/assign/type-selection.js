import {radiosByName, checkedValue, applyRadioStates, updateStateClasses} from './radio-button-service.js';

const INHERIT_BY_CONSTRAINT = {
	FREE: ['NO', 'YES'],
	NEVER: ['NO'],
	ALWAYS: ['YES'],
};

/**
 * Reads the rules for type selection that the ScopeRules establish
 */
function readRules(container) {
	const configEl = container.querySelector('#scope-rules-config');

	return configEl ? JSON.parse(configEl.textContent) : {};
}

/**
 * Wires up the type-selection fragment
 */
export function initTypeSelection(container) {
	const rulesByTarget = readRules(container);
	const targetRadios = radiosByName(container, 'assignTarget');
	const exceptionRadios = radiosByName(container, 'assignException');
	const inheritRadios = radiosByName(container, 'assignInherit');
	const allRadios = [...targetRadios, ...exceptionRadios, ...inheritRadios];

	function applyRules() {
		const target = checkedValue(targetRadios);
		const exceptionRules = rulesByTarget[target] ?? {};

		applyRadioStates(exceptionRadios, new Set(Object.keys(exceptionRules)));

		const exception = checkedValue(exceptionRadios);
		const inheritConstraint = exceptionRules[exception] ?? 'FREE';

		applyRadioStates(inheritRadios, new Set(INHERIT_BY_CONSTRAINT[inheritConstraint] ?? INHERIT_BY_CONSTRAINT.FREE));

		updateStateClasses(allRadios);
	}

	allRadios.forEach(radio => radio.addEventListener('change', applyRules));

	applyRules();
}
