$(document).ready(function () {
    function toggleAdSyncServiceDomain() {
        const adSyncServiceDomain = $("#adSyncServiceDomain");
        const integrationField = $("#integrationSelect");

        if (integrationField.val() === "AD_SYNC_SERVICE") {
            adSyncServiceDomain.css("display", "block");
        } else {
            adSyncServiceDomain.css("display", "none");
        }
    }

    $("#integrationSelect").on("change", toggleAdSyncServiceDomain);

    // Set initial visibility on page load
    toggleAdSyncServiceDomain();
});
