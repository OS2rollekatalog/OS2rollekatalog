// Handles the duplicate role group assignments cleanup report page
class DuplicateRoleGroupAssignmentsService {
    constructor() {
        const config = JSON.parse(document.getElementById("duplicate-rolegroup-config").textContent);

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

        document.addEventListener("click", (event) => {
            const button = event.target.closest(".js-delete-duplicates");
            if (button) {
                this.duplicateCleanupService.deleteDuplicates();
            }
        });
    }
}

document.addEventListener("DOMContentLoaded", () => {
    window.duplicateRoleGroupAssignmentsService = new DuplicateRoleGroupAssignmentsService();
    window.duplicateRoleGroupAssignmentsService.init();
});
