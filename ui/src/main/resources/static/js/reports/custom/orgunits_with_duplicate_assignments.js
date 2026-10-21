// Handles the duplicate assignments on orgUnits report pages (both user roles and role groups)
class DuplicateOrgUnitAssignmentsService {
    constructor() {
        const config = JSON.parse(document.getElementById("duplicate-orgunit-assignments-config").textContent);
        this.searchTxt = config.searchTxt;
        this.dropdownTxt = config.dropdownTxt;
        this.infoDefaultTxt = config.infoDefaultTxt;
        this.infoEmptyTxt = config.infoEmptyTxt;
        this.infoFilteredTxt = config.infoFilteredTxt;
        this.prevTxt = config.prevTxt;
        this.nextTxt = config.nextTxt;
        this.txtYes = config.btnYes;
        this.txtNo = config.btnNo;
        this.rows = config.rows || [];
        this.showItSystem = config.showItSystem;
        this.canEdit = config.canEdit;
        this.manageUrlBase = config.manageUrlBase;

        this.duplicateCleanupService = new DuplicateCleanupService({
            url: config.url,
            txtTitle: config.txtTitle,
            txtBody: config.txtBody,
            txtOrderConfirmed: config.txtOrderConfirmed,
            txtCleanupFailed: config.txtCleanupFailed,
            txtCleanupSucceeded: config.txtCleanupSucceeded,
            txtCleanupAlreadyRunning: config.txtCleanupAlreadyRunning,
            txtQueueSpinner: config.txtQueueSpinner,
            btnNo: config.btnNo,
            btnYes: config.btnYes,
            panelSelector: '#cleanup-panel'
        });
    }

    init() {
        window.token = $("meta[name='_csrf']").attr("content");

        const columns = [
            { data: 'ouName' },
            { data: 'roleName' }
        ];

        if (this.showItSystem) {
            columns.push({ data: 'itSystemName' });
        }

        columns.push(
            { data: 'inherit', render: (data) => data ? this.txtYes : this.txtNo },
            { data: 'filters' },
            { data: 'duplicateOf' },
            {
                data: 'ouUuid',
                orderable: false,
                searchable: false,
                render: (data) => {
                    if (!this.canEdit || !data) {
                        return '';
                    }
                    return '<a href="' + this.manageUrlBase + encodeURIComponent(data) + '"><em class="fa fa-pencil"></em></a>';
                }
            }
        );

        $('#duplicateTable').DataTable({
            paging: true,
            ordering: true,
            order: [[0, 'asc']],
            info: true,
            deferRender: true,
            pageLength: 100,
            autoWidth: false,
            data: this.rows,
            columns: columns,
            language: {
                search: this.searchTxt,
                lengthMenu: this.dropdownTxt,
                info: this.infoDefaultTxt,
                zeroRecords: this.infoEmptyTxt,
                infoEmpty: '',
                infoFiltered: this.infoFilteredTxt,
                paginate: {
                    next: this.nextTxt,
                    previous: this.prevTxt
                }
            }
        });

        document.addEventListener("click", (event) => {
            const button = event.target.closest(".js-delete-duplicates");
            if (button) {
                this.duplicateCleanupService.deleteDuplicates();
            }
        });
    }
}

document.addEventListener("DOMContentLoaded", () => {
    window.duplicateOrgUnitAssignmentsService = new DuplicateOrgUnitAssignmentsService();
    window.duplicateOrgUnitAssignmentsService.init();
});
