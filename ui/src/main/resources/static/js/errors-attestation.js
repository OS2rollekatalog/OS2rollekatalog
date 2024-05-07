

function errorHandler(fallbackMessage) {
    return function (response) {
        if (response.status === 403) {
            toastr.warning("Din session er udløbet, genindlæser");
            setTimeout(function(){
                location.reload();
            }, 2000);
            return;
        }
        if (response.responseText !== null && response.responseText !== "") {
            toastr.warning(response.responseText);
        } else {
            toastr.warning(fallbackMessage);
        }
    }
}

let defaultErrorHandler = errorHandler('Teknisk fejl');
