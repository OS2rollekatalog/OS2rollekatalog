/**
 * Handles report downloads (with optional "since" date filter) and
 * table initialization on the attestation module's reports page.
 */
class ReportsService {
    currentDate = null;

    constructor(config, datatableService) {
        this.config = config;
        this.datatableService = datatableService;
    }

    downloadReport(anchorElement) {
        let url;
        if (this.currentDate != null) {
            // toISOString converts to UTC, so add 12 hours first to avoid landing on the previous day
            const adjustedDate = new Date(this.currentDate.getTime() + 12 * 60 * 60 * 1000);
            url = anchorElement.href.split('?')[0] + "?since=" + encodeURIComponent(adjustedDate.toISOString().split('T')[0]);
        } else {
            url = anchorElement.href;
        }

        const confirmed = confirm("Download af rapporter kan tage flere minutter, og der kan kun hentes en rapport af gangen.\nForsæt?");
        if (confirmed) {
            $.ajax({
                url: this.config.preflightUrl,
                xhrFields: {
                    responseType: 'blob'
                },
                success: () => {
                    window.location = url;
                },
                error: () => {
                    toastr.warning('Rapport modulet er optaget');
                }
            });
        }
    }

    initDatePicker() {
        $('#datepickerDiv').datepicker()
            .on('changeDate', (e) => {
                this.currentDate = e.date;
            });
    }

    initTables() {
        $('#orgUnitTable').DataTable({
            columnDefs: [
                { orderable: false, targets: 2 }
            ],
            pageLength: 25,
            responsive: true,
            autoWidth: false,
            language: this.datatableService.defaultLanguageOptions
        });

        $('#itSystemTable').DataTable({
            columnDefs: [
                { orderable: false, targets: 1 }
            ],
            pageLength: 25,
            responsive: true,
            autoWidth: false,
            language: this.datatableService.defaultLanguageOptions
        });

        $('#allRolesTable').DataTable({
            searching: false,
            ordering: false,
            paging: false,
            responsive: true,
            autoWidth: false
        });

        $.fn.dataTable.ext.type.order['date-dk-pre'] = function (d) {
            if (!d || d === '') {
                return 0;
            }
            // Parse Danish date format dd/MM-YYYY
            const parts = d.match(/(\d{2})\/(\d{2})-(\d{4})/);
            if (!parts) {
                return 0;
            }

            const day = parseInt(parts[1], 10);
            const month = parseInt(parts[2], 10);
            const year = parseInt(parts[3], 10);
            // Return timestamp for correct sorting
            return new Date(year, month - 1, day).getTime();
        };

        $('#auditTable').DataTable({
            columnDefs: [
                { orderable: false, targets: 4 },
                { type: "date-dk", targets: [0] }
            ],
            order: [[0, "desc"]],
            pageLength: 25,
            responsive: true,
            autoWidth: false,
            language: this.datatableService.defaultLanguageOptions
        });
    }
}

function initReportsDownloadListener(reportsService) {
    if (window.reportsDownloadListenerAttached) {
        return;
    }
    window.reportsDownloadListenerAttached = true;

    document.addEventListener("click", (event) => {
        const anchor = event.target.closest(".js-download-report");
        if (!anchor) {
            return;
        }
        event.preventDefault();
        reportsService.downloadReport(anchor);
    });
}

document.addEventListener("DOMContentLoaded", () => {
    const configElement = document.getElementById("reports-config");
    const config = JSON.parse(configElement.textContent);

    window.token = document.querySelector("meta[name='_csrf']").getAttribute("content");

    const datatableService = new DatatableService();
    const reportsService = new ReportsService(config, datatableService);

    reportsService.initDatePicker();
    reportsService.initTables();
    initReportsDownloadListener(reportsService);
});
