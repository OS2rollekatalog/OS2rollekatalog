$(document).ready(() => {
    const config = JSON.parse(document.getElementById('user-role-modal-config').textContent);

    window.titlesEnabled = config.titlesEnabled;
    window.modalAjaxService = new ModalAjaxService();
    window.userRoleModalService = new UserRoleModalService(config);
    window.userRoleModalService.init();
});
