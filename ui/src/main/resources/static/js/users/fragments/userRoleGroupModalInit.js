$(document).ready(() => {
    const config = JSON.parse(document.getElementById('role-group-modal-config').textContent);

    window.modalAjaxService = new ModalAjaxService();
    window.roleGroupModalService = new RoleGroupModalService(config);
    window.roleGroupModalService.init();
});
