

function errorHandler(fallbackMessage) {
    return function (response) {
        if (response.status === 403) {
            $.notify({
                message: "Din session er udløbet, genindlæser",
                status: 'warning',
                timeout: 3000
            });
            setTimeout(function(){
                location.reload();
            }, 3000);
            return;
        }
        if (response.responseText != null) {
            $.notify({
                message: response.responseText,
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
