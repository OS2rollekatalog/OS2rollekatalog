function errorHandler(fallbackMessage) {
    return function (response) {
        const detail = response.responseJSON && response.responseJSON.detail;

        if (response.status === 403) {
            if (detail) {
                toastr.warning(detail);
                return;
            }

            toastr.warning("Din session er udløbet, genindlæser");
            setTimeout(function(){
                location.reload();
            }, 2000);
            return;
        }
        if (detail) {
            toastr.warning(detail);
        } else if (response.responseText !== null && response.responseText !== "") {
            toastr.warning(response.responseText);
        } else {
            toastr.warning(fallbackMessage);
        }
    }
}

let defaultErrorHandler = errorHandler('Teknisk fejl');
