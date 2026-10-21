/**
 * Global toastr configuration for the footer fragment, applied to every page
 * that includes it (i.e. essentially every page in the application).
 */
toastr.options = {
    positionClass: "toast-top-right toastr-buffer"
};

// shows bootstrap tooltips for any element with data-tooltip
$(document.body).tooltip({
    selector: '[data-toggle="tooltip"]',
    container: 'body',
    boundary: 'window'
});
