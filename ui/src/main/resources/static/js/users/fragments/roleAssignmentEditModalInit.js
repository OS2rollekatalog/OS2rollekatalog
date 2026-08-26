$(document).ready(() => {
    const config = JSON.parse(document.getElementById('role-assignment-edit-modal-config').textContent);

    window.modalAjaxService = new ModalAjaxService();
    window.userRoleEditModalService = new UserRoleEditModalService(config);
    window.userRoleEditModalService.init();
});
