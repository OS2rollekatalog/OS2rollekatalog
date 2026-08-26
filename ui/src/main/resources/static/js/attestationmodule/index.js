/**
 * Handles the attestation run dashboard: dynamic tab loading and per-run
 * datatable initialization. Relies on the shared TabService for fragment loading
 * and DatatableService for the standard Danish language options.
 */
class AttestationDashboardPage {
    constructor(config, tabService, datatableService) {
        this.config = config;
        this.tabService = tabService;
        this.datatableService = datatableService;
    }

    init() {
        const navTabElements = document.querySelectorAll('.nav-link');

        for (const navTab of navTabElements) {
            const runId = navTab.getAttribute('data-run-id');
            const containerElement = document.getElementById(`ovtab${runId}`);
            const url = `${this.config.tabContentUrl}${runId}`;
            this.tabService.initLoadContentOnClick(navTab, containerElement, url, () => this.initDatatable(runId));
        }

        if (navTabElements.length > 0) {
            navTabElements[0].click();
        }
    }

    initDatatable(runId) {
        $(`.dashboardTable_${runId}`).DataTable({
            "bSort": false,
            "paging": false,
            "responsive": true,
            "dom": "<'row'<'col-sm-12'tr>>",
            "language": this.datatableService.defaultLanguageOptions
        });
    }
}

function initAttestationDashboard() {
    // TabService reads the global 'token' variable when fetching fragments
    window.token = document.querySelector("meta[name='_csrf']").getAttribute('content');

    const configElement = document.getElementById('attestation-dashboard-config');
    const config = JSON.parse(configElement.textContent);

    window.datatableService = window.datatableService || new DatatableService();

    const tabService = new TabService();
    const page = new AttestationDashboardPage(config, tabService, window.datatableService);
    page.init();
}

document.addEventListener('DOMContentLoaded', initAttestationDashboard);
