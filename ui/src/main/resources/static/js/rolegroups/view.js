// View page for a role group: loads user/OU role tabs, shows flash messages,
// and initializes the (disabled) requester/approver select2 dropdowns.

document.addEventListener("DOMContentLoaded", () => {
    const config = JSON.parse(document.getElementById("rolegroup-view-config").textContent);

    // Expose globals expected by ajax-loaded fragments (assignedUsersFragmentView,
    // assignedOrgUnitsFragment), mirroring the original inline-script's var declarations.
    window.token = $("meta[name='_csrf']").attr("content");
    window.url = config.url;
    window.roleId = config.roleId;
    window.UIUrl = config.uiUrl;
    window.userRestUrl = config.userRestUrl;
    window.ouRestUrl = config.ouRestUrl;
    window.fieldUpdatedMsg = config.fieldUpdatedMsg;
    window.fieldNotUpdatedMsg = config.fieldNotUpdatedMsg;
    window.txtQueueSpinner = config.txtQueueSpinner;
    window.titlesEnabled = config.titlesEnabled;

    window.notificationService = window.notificationService || new NotificationService();

    window.userService.loadRolesFragment();
    window.orgUnitService.loadRolesFragment();

    if (config.infoMessage) {
        window.notificationService.showInfoNotification(config.infoMessage);
    }

    if (config.errorMessage) {
        window.notificationService.showErrorNotification(config.errorMessage);
    }

    window.select2Service = window.select2Service || new Select2Service();
    window.select2Service.initSelect("#requesterSettingsSelect, #approverSettingsSelect", {
        placeholder: "",
        allowClear: false,
        multiple: true
    });
});

window.userService = {
    loadRolesFragment() {
        new QueueDrainService("#users_menu", { message: window.txtQueueSpinner }).watch(() => {
            $("#users_menu").load(window.UIUrl + window.roleId + "/assignedUsersFragmentView", () => {
                fragShowDataTableFun("#listTableUsers", 0);
            });
        });
    }
};

window.orgUnitService = {
    loadRolesFragment() {
        $("#ous_menu").load(window.UIUrl + window.roleId + "/assignedOrgUnitsFragment", () => {
            fragShowDataTableFun("#listTableOus", 0);
        });
    }
};
