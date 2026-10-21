import {checkedValue, initRadioButtonGroup, radiosByName} from '../radio-button-service.js';

export function init(container) {
	initRadioButtonGroup(container, 'substituteIncluded');
}

export function applySelection(container, selection) {
	selection.updateScopeFields({
		includeSubstitutes: checkedValue(radiosByName(container, 'substituteIncluded')) === 'true',
	});
}
