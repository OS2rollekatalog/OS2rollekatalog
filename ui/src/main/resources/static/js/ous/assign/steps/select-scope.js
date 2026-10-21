import {initTypeSelection} from '../type-selection.js';
import {radiosByName, updateStateClasses} from '../radio-button-service.js';

function checkedValue(container, name) {
	return container.querySelector(`input[name="${name}"]:checked`)?.value ?? null;
}

function readChoice(container) {
	return {
		target: checkedValue(container, 'assignTarget'),
		exception: checkedValue(container, 'assignException'),
		inherit: checkedValue(container, 'assignInherit') === 'YES',
	};
}

function isLocked(container) {
	return container.querySelector('#assignmentTypeView')?.dataset.locked === 'true';
}

// Skip initTypeSelection when locked, or its rule-driven enabling re-enables these radios.
export function init(container) {
	if (isLocked(container)) {
		const allRadios = ['assignTarget', 'assignException', 'assignInherit']
			.flatMap(name => radiosByName(container, name));
		updateStateClasses(allRadios);
		return;
	}

	initTypeSelection(container);
}

export function applySelection(container, selection) {
	selection.setScopeChoice(readChoice(container));
}

export function answerPayload(container) {
	return readChoice(container);
}
