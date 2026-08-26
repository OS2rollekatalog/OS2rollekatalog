

// $.notify interpolates its message straight into an HTML string (see notify.js in angle-webjar),
// so anything coming from the server must be escaped. Error bodies can carry names from the database -
// a role name or IT system name - and those are set by administrators, by the API and by AD/DMP sync.
function escapeNotificationHtml(text) {
    return $("<div>").text(text).html();
}

function errorHandler(fallbackMessage) {
    return function (response) {
        if (response.responseText !== null && response.responseText !== undefined && response.responseText.startsWith("{")) {
            let responseObj = JSON.parse(response.responseText);
            $.notify({
                message: escapeNotificationHtml(responseObj.error),
                status: 'danger',
                timeout: 4000
            });
            return;
        }
        if (response.responseText != null && response.responseText !== "") {
            $.notify({
                message: escapeNotificationHtml(response.responseText),
                status: 'danger',
                timeout: 4000
            });
        } else if (response.status === 403) {
            $.notify({
                message: "Adgang nægtet: Enten er din session udløbet, eller du har ikke de nødvendige rettigheder til at tilgå denne funktion.",
                status: 'danger',
                timeout: 4000
            });
        } else {
            $.notify({
                message: fallbackMessage,
                status: 'danger',
                timeout: 4000
            });
        }
    }
}

let defaultErrorHandler = errorHandler('Ukendt fejl');
