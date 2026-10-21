import {initCheckboxTable} from '../../../fragments/checkboxTable.js';

const tables = new WeakMap();

export function init(container) {
	tables.set(container, initCheckboxTable(container, {
		table: '#exceptedUsersStepTable',
		checkboxSelector: '.excepted-users-step-checkbox',
	}));
}

export function applySelection(container, selection) {
	selection.updateScopeFields({exceptedUserUuids: tables.get(container)?.getSelectedIds() ?? []});
}

export function destroy(container) {
	tables.get(container)?.destroy();
	tables.delete(container);
}
