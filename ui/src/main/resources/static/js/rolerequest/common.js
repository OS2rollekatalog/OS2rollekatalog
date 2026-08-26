/**
 * Handles the expandable "child row" detail view for role-group tables (shows
 * the nested user roles under a role group). Used by several pages that render
 * a role-group DataTable with a dt-control column for row expansion.
 */
class ExpandableRoleGroupTableService {
    constructor(detailsUrl) {
        // Falls back to window.detailsUrl for callers that set it as a global
        // before instantiating this service (kept for backwards compatibility
        // with pages/fragments not yet updated to pass it explicitly).
        this.detailsUrl = detailsUrl || window.detailsUrl;
    }

    initUserRoleTable() {
        $(".roleGroupUserRoleTable").DataTable({
            destroy: true,
            pageLength: 25,
            responsive: true,
            language: {
                search: "Søg",
                lengthMenu: "_MENU_ rækker per side",
                info: "Viser _START_ til _END_ af _TOTAL_ rækker",
                zeroRecords: "Ingen data...",
                infoEmpty: "",
                infoFiltered: "(ud af _MAX_ rækker)",
                paginate: {
                    previous: "Forrige",
                    next: "Næste"
                }
            }
        });
    }

    openCloseDetails(table, e) {
        const tr = e.target.closest('tr');
        const row = table.row(tr);
        if (row.child.isShown()) {
            row.child.hide();
        } else {
            row.child(this.formatDetails(row.data())).show();
        }
        $(tr).toggleClass('dt-hasChild');
    }

    formatDetails(rowData) {
        const div = $('<div/>')
            .addClass('loading')
            .text('Henter...');

        $.ajax({
            url: `${this.detailsUrl}/${rowData[0]}/userroles`,
            data: {
                name: rowData.name
            },
            success: (data) => {
                div.html(data).removeClass('loading');
                this.initUserRoleTable();
            }
        });

        return div;
    }
}

/**
 * Service class meant to provide common custom utility for DataTables.
 */
class DataTableService {
    constructor() {}

    toggleRowDetailsFromClass(event, table) {
        this.#toggleDetailVisibility(event, table, () => this.#findDetailView(event));
    }

    toggleRowDetailsCustom(event, table, formattingFunction) {
        this.#toggleDetailVisibility(event, table, formattingFunction);
    }

    toggleRowDetailsFromServer(event, table, fullDetailUrl) {
        this.#toggleDetailVisibility(event, table,
            (rowData) => this.#fetchDetailViewFromServer(fullDetailUrl, rowData)
        );
    }

    /**
     * Toggles the visibility of a row's detail view
     * @param {Function} formattingFunction a function returning the detail view of the row, taking the row's data as its first argument
     */
    async #toggleDetailVisibility(event, table, formattingFunction) {
        const tr = event.target.closest('tr');
        const row = table.row(tr);
        if (row.child.isShown()) {
            row.child.hide();
        } else {
            const detailView = await formattingFunction();
            row.child(detailView).show();
        }
        $(tr).toggleClass('dt-hasChild');
    }

    /**
     * Fetches the detail view for a row from the server, using the url provided
     * @param {string} fullDetailsUrl the url from which to retrieve the detail view
     * @param {Object} rowData the table data for the row
     * @returns {Promise<HTMLElement>} a div element containing the detail view
     */
    async #fetchDetailViewFromServer(fullDetailsUrl, rowData) {
        const div = document.createElement('div');
        div.classList.add('loading');
        div.textContent = 'Henter';

        const response = await fetch(fullDetailsUrl);

        if (!response.ok) {
            console.error('could not retrieve details for row from ' + fullDetailsUrl, response.statusText);
        }

        div.innerHTML = await response.text();
        div.classList.remove('loading');

        return div;
    }

    #findDetailView(event) {
        const tr = event.target.closest('tr');
        const detailView = tr.querySelector('.detailview');
        const div = document.createElement('div');
        div.innerHTML = detailView.innerHTML;
        div.classList.add('col-sm-12');
        return div;
    }
}

class TabService {
    nameSpace;

    constructor(nameSpace) {
        this.nameSpace = nameSpace;
    }

    rememberTab(href) {
        sessionStorage.setItem(this.nameSpace + '_active_tab', href);
    }

    restoreTab() {
        const href = sessionStorage.getItem(this.nameSpace + '_active_tab');
        if (href) {
            $('a[href="#' + href + '"]').tab('show');
        }
    }
}
