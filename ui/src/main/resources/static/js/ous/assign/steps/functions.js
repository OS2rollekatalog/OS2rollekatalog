import {initCheckboxTable} from '../../../fragments/checkboxTable.js';

const tables = new WeakMap();

export function init(container) {
	tables.set(container, initCheckboxTable(container, {
		table: '#functionsStepTable',
		checkboxSelector: '.functions-step-checkbox',
	}));
}

export function applySelection(container, selection) {
	selection.updateScopeFields({functionUuids: tables.get(container)?.getSelectedIds() ?? []});
}

export function destroy(container) {
	tables.get(container)?.destroy();
	tables.delete(container);
}
