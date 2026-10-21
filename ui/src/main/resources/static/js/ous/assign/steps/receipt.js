import {initDateRangePicker} from '../../../fragments/dateRangePicker.js';

const pickers = new WeakMap();

export function init(container) {
	const contentEl = container.querySelector('#receiptStepContent');
	const startDate = contentEl?.dataset.startDate ?? null;
	const stopDate = contentEl?.dataset.stopDate ?? null;

	pickers.set(container, initDateRangePicker(container, {
		start: '#receiptStartDatePicker',
		end: '#receiptStopDatePicker',
		startDate,
		stopDate,
	}));
}

export function applySelection(container, selection) {
	const range = pickers.get(container);

	selection.setCommonFields({
		startDate: range?.startDate() ?? null,
		stopDate: range?.stopDate() ?? null,
		caseNumber: container.querySelector('#receiptCaseNumber')?.value.trim() || null,
	});
}

export function destroy(container) {
	pickers.get(container)?.destroy();
	pickers.delete(container);
}
