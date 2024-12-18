

function errorHandler(fallbackMessage) {
    return function (response) {
        if (response.responseText !== null && response.responseText !== undefined && response.responseText.startsWith("{")) {
            let responseObj = JSON.parse(response.responseText);
            $.notify({
                message: responseObj.error,
                status: 'danger',
                timeout: 4000
            });
            return;
        }
        if (response.responseText != null && response.responseText !== "") {
            $.notify({
                message: response.responseText,
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
