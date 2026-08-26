// Handles the duplicate user role assignments cleanup report page
class DuplicateUserRoleAssignmentsService {
    constructor() {
        const config = JSON.parse(document.getElementById("duplicate-userrole-config").textContent);
        this.searchTxt = config.searchTxt;
        this.dropdownTxt = config.dropdownTxt;
        this.infoDefaultTxt = config.infoDefaultTxt;
        this.infoEmptyTxt = config.infoEmptyTxt;
        this.infoFilteredTxt = config.infoFilteredTxt;
        this.prevTxt = config.prevTxt;
        this.nextTxt = config.nextTxt;
        this.rows = config.rows;
        this.canEdit = config.canEdit;
        this.manageUrlBase = config.manageUrlBase;

        this.duplicateCleanupService = new DuplicateCleanupService({
            url: config.url,
            txtTitle: config.txtTitle,
            txtBody: config.txtBody,
            txtOrderConfirmed: config.txtOrderConfirmed,
            txtCleanupFailed: config.txtCleanupFailed,
            txtCleanupSucceeded: config.txtCleanupSucceeded,
            txtQueueSpinner: config.txtQueueSpinner,
            btnNo: config.btnNo,
            btnYes: config.btnYes,
            panelSelector: '#cleanup-panel'
        });
    }

    init() {
        window.token = $("meta[name='_csrf']").attr("content");

        $('#duplicateTable').DataTable({
            paging: true,
            ordering: true,
            order: [[0, 'asc']],
            info: true,
            deferRender: true,
            pageLength: 100,
            autoWidth: false,
            data: this.rows,
            columns: [
                { data: 'name' },
                { data: 'userId' },
                { data: 'roleName' },
                { data: 'itSystemName' },
                { data: 'message' },
                {
                    data: 'uuid',
                    orderable: false,
                    searchable: false,
                    render: (data) => {
                        if (!this.canEdit || !data) {
                            return '';
                        }
                        return '<a href="' + this.manageUrlBase + encodeURIComponent(data) + '"><em class="fa fa-pencil"></em></a>';
                    }
                }
            ],
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
    window.duplicateUserRoleAssignmentsService = new DuplicateUserRoleAssignmentsService();
    window.duplicateUserRoleAssignmentsService.init();
});
