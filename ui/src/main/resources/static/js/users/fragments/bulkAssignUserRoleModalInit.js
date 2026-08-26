$(document).ready(() => {
    const config = JSON.parse(document.getElementById("bulk-assign-user-role-modal-config").textContent);

    window.modalAjaxService = window.modalAjaxService || new ModalAjaxService();
    window.bulkAssignRoleModalService = new BulkAssignRoleModalService(config);
    window.bulkAssignRoleModalService.init();

    window.initBulkAssignModalDatePickers();
});
