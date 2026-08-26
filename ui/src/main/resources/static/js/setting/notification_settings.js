/**
 * Handles the notification settings page: per-notification-type checkboxes
 * and the save action, which confirms via SweetAlert whether previously
 * created notifications of deselected types should also be deleted.
 */
let modifications = false;
let settingService;

document.addEventListener("DOMContentLoaded", () => {
    const config = JSON.parse(document.getElementById("notification-setting-config").textContent);
    const token = document.querySelector("meta[name='_csrf']").getAttribute("content");

    settingService = new SettingService(config, token);
    settingService.init();
});

document.addEventListener("click", (event) => {
    if (event.target.closest(".js-save-settings-btn")) {
        event.preventDefault();
        settingService.submitForm();
    }
});

class SettingService {
    constructor(config, token) {
        this.config = config;
        this.token = token;
        this.sweetAlertService = new SweetAlertService();
    }

    init() {
        $(".notificationtypeCbx").on("change", (event) => {
            this.handleCheckbox(event.target, $(event.target).data("id"));
        });
    }

    submitForm() {
        if (!modifications) {
            $.notify({
                message: this.config.successMsg
            }, {
                status: "success",
                autoHideDelay: 4000
            });
            return;
        }

        const checkboxes = $(".notificationtypeCbx");
        const notificationTypes = {};

        for (let i = 0; i < checkboxes.length; i++) {
            const key = $(checkboxes[i]).data("id");
            const value = $("#" + key).val();

            notificationTypes[key] = value;
        }

        this.sweetAlertService.confirm(
            this.config.deleteDeselectedNotificationsHeader,
            this.config.deleteDeselectedNotificationsText,
            this.config.btnYes,
            this.config.btnNo,
            () => this.postSettings(notificationTypes, true),
            {
                onCancel: () => this.postSettings(notificationTypes, false)
            }
        );
    }

    postSettings(notificationTypes, deleteAlreadyCreated) {
        modifications = false;

        $.ajax({
            method: "POST",
            url: this.config.ajaxUrl,
            headers: {
                "X-CSRF-TOKEN": this.token
            },
            contentType: "application/json",
            data: JSON.stringify({
                deleteAlreadyCreated: deleteAlreadyCreated,
                notificationTypes: notificationTypes
            })
        }).done(() => {
            $.notify({
                message: this.config.successMsg
            }, {
                status: "success",
                autoHideDelay: 4000
            });
        }).fail(errorHandler(this.config.failedMsg));
    }

    handleCheckbox(obj, id) {
        const checked = $(obj).prop("checked");
        $("#" + id).val(checked);

        modifications = true;
    }
}
