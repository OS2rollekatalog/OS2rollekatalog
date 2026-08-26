/**
 * Handles the attestation admin overview page: lazily loading run tabs
 * and initializing the org-unit / IT-system DataTables for each run.
 */
class AttestationAdminPage {
    constructor(config) {
        this.config = config;
        this.tabService = null;
    }

    init() {
        this.tabService = new TabService();
        const navTabElements = document.querySelectorAll('.nav-link');

        for (const navTab of navTabElements) {
            const id = navTab.getAttribute('data-run-id');
            const containerElement = document.getElementById(`ovtab${id}`);
            const url = `${this.config.tabContentUrl}${id}`;
            this.tabService.initLoadContentOnClick(navTab, containerElement, url, () => this.initDatatable(id));
        }

        // Preserves original behavior: the first tab is opened automatically on load
        navTabElements[0].click();
    }

    formatDetails(rowData) {
        const div = $('<div/>')
            .addClass('loading')
            .text('Henter...');

        $.ajax({
            url: `${this.config.detailsUrl}/${rowData[0]}`,
            data: {
                name: rowData.name
            },
            success: (data) => {
                div.html(data).removeClass('loading');
            }
        });

        return div;
    }

    openCloseDetails(table, e) {
        const tr = e.target.closest('tr');
        const row = table.row(tr);

        if (row.child.isShown()) {
            row.child.hide();
        } else {
            // NOTE: second argument here is a pre-existing no-op (formatDetails only takes one param),
            // preserved as-is to keep behavior identical
            row.child(this.formatDetails(row.data(), true)).show();
        }

        $(tr).toggleClass('dt-hasChild');
    }

    initDatatable(runId) {
        const datatableLanguage = {
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
        };

        const ouTable = $(`#orgUnitTable${runId}`).DataTable({
            pageLength: 25,
            responsive: true,
            autoWidth: false,
            stateSave: true,
            language: datatableLanguage
        });
        ouTable.on('click', 'td.dt-control', (e) => this.openCloseDetails(ouTable, e));

        const itsTable = $(`#itSystemsTable${runId}`).DataTable({
            pageLength: 25,
            responsive: true,
            stateSave: true,
            autoWidth: false,
            language: datatableLanguage
        });
        itsTable.on('click', 'td.dt-control', (e) => this.openCloseDetails(itsTable, e));
    }
}

function initAttestationAdminPage() {
    const configElement = document.getElementById('attestation-admin-config');
    const config = JSON.parse(configElement.textContent);

    // CSRF token exposed as a true global so shared services (e.g. TabService)
    // can read it as a bare identifier
    window.token = document.querySelector("meta[name='_csrf']").getAttribute('content');

    const page = new AttestationAdminPage(config);
    page.init();
}

document.addEventListener('DOMContentLoaded', initAttestationAdminPage);
