import {createDataTable} from './dataTable.js';

const CHECKBOX_COLUMN ={className: 'details-control', searchable: false, orderable: false};

function buildColumns(tableEl) {
	const headerCount = tableEl.tHead?.rows[0]?.cells.length ?? 0;
	const trailing = Math.max(headerCount - 1, 0);

	return [CHECKBOX_COLUMN, ...Array.from({length: trailing}, () => ({}))];
}

function resolveTable(root, table) {
	const tableEl = typeof table === 'string' ? root.querySelector(table) : table;
	if (!tableEl) {
		throw new Error(`checkboxTable: no table matching ${table}`);
	}

	return tableEl;
}

/**
 * Wires a table whose first column holds row-selection checkboxes, each carrying its
 * item id in data-id. Selection is tracked off the DOM, surviving paging and searching.
 *
 * @param {HTMLElement} root element containing the table; bounds every lookup
 * @param {Object} options
 * @param {string|HTMLElement} options.table the table, or a selector within root
 * @param {string} options.checkboxSelector selector for the checkboxes within the table
 * @param {number} [options.orderColumn] column index to sort by initially; column 0 is the
 *   checkbox column and is not sortable
 * @param {number} [options.pageLength] rows per page
 * @param {Iterable<string>} [options.selectedIds] ids checked on load; defaults to whatever
 *   the server already rendered as checked
 * @param {(ids: string[]) => void} [options.onChange] called after every selection change
 * @returns {{getSelectedIds: function(): string[], setSelectedIds: function(Iterable<string>): void,
 *   clear: function(): void, isSelected: function(string): boolean, size: function(): number,
 *   destroy: function(): void}}
 */
export function initCheckboxTable(root, {table, checkboxSelector, orderColumn = 1, pageLength = 25, selectedIds, onChange} = {}) {
	const tableEl = resolveTable(root, table);
	const initialIds = selectedIds ?? [...tableEl.querySelectorAll(`${checkboxSelector}:checked`)].map(checkbox => checkbox.dataset.id);
	const selected = new Set(initialIds);

	const dataTable = createDataTable(tableEl, {orderColumn, pageLength, columns: buildColumns(tableEl)});

	function restoreCheckedState() {
		tableEl.querySelectorAll(checkboxSelector).forEach(checkbox => {
			checkbox.checked = selected.has(checkbox.dataset.id);
		});
	}

	function handleChange(event) {
		const checkbox = event.target.closest(checkboxSelector);
		if (!checkbox || !tableEl.contains(checkbox)) {
			return;
		}

		if (checkbox.checked) {
			selected.add(checkbox.dataset.id);
		} else {
			selected.delete(checkbox.dataset.id);
		}

		onChange?.(Array.from(selected));
	}

	tableEl.addEventListener('change', handleChange);
	// DataTables detaches off-page rows, dropping their checked state
	dataTable.on('draw.checkboxTable', restoreCheckedState);
	restoreCheckedState();

	return {
		getSelectedIds: () => Array.from(selected),
		setSelectedIds(ids) {
			selected.clear();
			for (const id of ids) {
				selected.add(id);
			}
			restoreCheckedState();
			onChange?.(Array.from(selected));
		},
		clear() {
			this.setSelectedIds([]);
		},
		isSelected: id => selected.has(id),
		size: () => selected.size,
		destroy() {
			tableEl.removeEventListener('change', handleChange);
			dataTable.off('draw.checkboxTable');
			dataTable.destroy();
		},
	};
}
