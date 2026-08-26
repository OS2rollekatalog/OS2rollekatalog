/**
 * Handles the KOMBIT settings page: the "hide IT systems by default"
 * checkbox and the save notification.
 */
document.addEventListener("DOMContentLoaded", () => {
    const config = JSON.parse(document.getElementById("kombit-setting-config").textContent);

    showSavedNotification(config.saved, config.successMsg, config.failedMsg);
    initItSystemsHiddenCheckbox();
});

function showSavedNotification(saved, successMsg, failedMsg) {
    if (typeof saved === "undefined" || saved === null) {
        return;
    }

    if (saved) {
        $.notify({ message: successMsg }, { status: "success", autoHideDelay: 4000 });
    } else {
        $.notify({ message: failedMsg }, { status: "warning", autoHideDelay: 4000 });
    }
}

function initItSystemsHiddenCheckbox() {
    $("#itsystemshidden-checkbox").on("change", function () {
        $("#itSystemsHiddenByDefault").val(this.checked);
    });
}
