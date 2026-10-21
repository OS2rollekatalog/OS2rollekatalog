/**
 * Adapter for DataTables, loaded as a classic script alongside jQuery.
 * Mirrors fragShowDataTableFun in fragments/datatables.js; keep the two in sync until the classic one is gone.
 */

/**
 * @param {HTMLElement} tableEl the table
 * @param {Object} options
 * @param {number} options.orderColumn column index to sort by initially
 * @param {number} options.pageLength rows per page
 * @param {Array<Object>} [options.columns] DataTables column definitions
 * @param {Array<Object>} [options.data] the rows; when given, only the rows of the shown page are built
 * @param {boolean} [options.saveState] remember search, sorting and paging under the same key as fragShowDataTableFun
 */
export function createDataTable(tableEl, {orderColumn, pageLength, columns, data, saveState = false}) {
	return $(tableEl).DataTable({
		...(data === undefined ? {} : {data, deferRender: true}),
		destroy: true,
		paging: true,
		ordering: true,
		order: [[orderColumn, 'asc']],
		columns,
		autoWidth: false,
		info: true,
		...(saveState ? savedStateOptions() : {stateSave: false}),
		pageLength,
		language: readLanguage(),
	});
}

function savedStateOptions() {
	const keyOf = settings => `DataTables_${settings.sTableId}_${window.location.pathname}`;

	return {
		stateSave: true,
		stateSaveCallback: (settings, state) => localStorage.setItem(keyOf(settings), JSON.stringify(state)),
		stateLoadCallback: settings => JSON.parse(localStorage.getItem(keyOf(settings))),
		stateDuration: 0,
	};
}

function readLanguage() {
	const configEl = document.getElementById('datatables-config');
	if (!configEl) {
		throw new Error('dataTable: missing #datatables-config, include fragments/datatables');
	}

	const config = JSON.parse(configEl.textContent);

	return {
		search: config.searchTxt,
		lengthMenu: config.dropdownTxt,
		info: config.infoDefaultTxt,
		zeroRecords: config.infoEmptyTxt,
		infoEmpty: '',
		infoFiltered: config.infoFilteredTxt,
		paginate: {next: config.nextTxt, previous: config.prevTxt},
	};
}
