/**
 * Bootstrap and shared helper functions for the "fragments/datatables :: datatables"
 * fragment. Reads localized strings and the optional default order column from the
 * "datatables-config" JSON block rendered by the fragment.
 *
 * fragShowDataTableFun, showDataTableWithHiddenColumns, dataTablesToggleColumn and
 * dataTablesRefreshIcons are exposed on window because they are called from other
 * pages/fragments (including via inline attributes not yet covered by ROL-433).
 * window.currentTable is expected to be set by the calling page after creating its
 * table instance - same contract as before this refactor.
 */
function initDatatablesFragment() {
    const configElement = document.getElementById("datatables-config");
    const config = JSON.parse(configElement.textContent);

    const languageOptions = {
        search: config.searchTxt,
        lengthMenu: config.dropdownTxt,
        info: config.infoDefaultTxt,
        zeroRecords: config.infoEmptyTxt,
        infoEmpty: "",
        infoFiltered: config.infoFilteredTxt,
        paginate: {
            next: config.nextTxt,
            previous: config.prevTxt
        }
    };

    function fragShowDataTableFun(jqSelector, orderColumn, pagingLength = 100, stateId = null, columns = null) {
        return $(jqSelector).DataTable({
            destroy: true,
            paging: true,
            ordering: true,
            order: [
                [orderColumn, "asc"]
            ],
            columns: columns,
            autoWidth: false,
            info: true,
            stateSave: true,
            stateSaveCallback: (settings, data) => {
                const defaultKey = "DataTables_" + settings.sTableId + "_" + window.location.pathname;
                const key = stateId == null ? defaultKey : stateId;
                localStorage.setItem(key, JSON.stringify(data));
            },
            stateLoadCallback: (settings) => {
                const defaultKey = "DataTables_" + settings.sTableId + "_" + window.location.pathname;
                const key = stateId == null ? defaultKey : stateId;
                return JSON.parse(localStorage.getItem(key));
            },
            stateDuration: 0,
            pageLength: pagingLength,
            language: languageOptions
        });
    }

    function showDataTableWithHiddenColumns(jqSelector, orderColumn, hideColumns, pagingLength = 100, stateId = null) {
        return $(jqSelector).DataTable({
            paging: true,
            ordering: true,
            order: [
                [orderColumn, "asc"]
            ],
            autoWidth: false,
            info: true,
            stateSave: true,
            stateSaveCallback: (settings, data) => {
                const defaultKey = "DataTables_" + settings.sTableId + "_" + window.location.pathname;
                const key = stateId == null ? defaultKey : stateId;
                // Used in user -> userRole assignment: persist the "show inactive" toggle alongside the table state
                data.showInactive = window.showInactiveState !== undefined ? window.showInactiveState : true;
                localStorage.setItem(key, JSON.stringify(data));
            },
            stateLoadCallback: (settings) => {
                const defaultKey = "DataTables_" + settings.sTableId + "_" + window.location.pathname;
                const key = stateId == null ? defaultKey : stateId;
                const saved = localStorage.getItem(key);
                if (saved) {
                    const data = JSON.parse(saved);
                    window.showInactiveState = data.showInactive !== undefined ? data.showInactive : true;
                    return data;
                }
                return null;
            },
            pageLength: pagingLength,
            columnDefs: [
                { targets: hideColumns, visible: false }
            ],
            language: languageOptions
        });
    }

    function dataTablesRefreshIcons(dropDownElem) {
        if (!window.currentTable) {
            return false;
        }

        dropDownElem.find("a").each(function () {
            const cId = $(this).data("cid");

            if (window.currentTable.column(cId).visible()) {
                $(this).find("em").addClass("fa-check");
                $(this).find("em").removeClass("fa-minus");
            } else {
                $(this).find("em").addClass("fa-minus");
                $(this).find("em").removeClass("fa-check");
            }
        });
    }

    function dataTablesToggleColumn(elem) {
        if (!window.currentTable) {
            return false;
        }

        const cId = $(elem).data("cid");

        window.currentTable.column(cId).visible(!window.currentTable.column(cId).visible());

        const dropDownElem = $(elem).parent();
        dataTablesRefreshIcons(dropDownElem);

        return false;
    }

    // Plug-in for sorting checkboxes
    // from: https://datatables.net/plug-ins/sorting/custom-data-source/dom-checkbox
    $.fn.dataTable.ext.order["dom-checkbox"] = function (settings, col) {
        return this.api().column(col, { order: "index" }).nodes().map((td) => {
            return $("input", td).prop("checked") ? "1" : "0";
        });
    };

    window.fragShowDataTableFun = fragShowDataTableFun;
    window.showDataTableWithHiddenColumns = showDataTableWithHiddenColumns;
    window.dataTablesToggleColumn = dataTablesToggleColumn;
    window.dataTablesRefreshIcons = dataTablesRefreshIcons;

    document.addEventListener("DOMContentLoaded", () => {
        let orderColumn = 0;
        if (config.orderColumnValue) {
            orderColumn = config.orderColumnValue;
        }

        fragShowDataTableFun(".listTable", orderColumn);
    });
}

initDatatablesFragment();
