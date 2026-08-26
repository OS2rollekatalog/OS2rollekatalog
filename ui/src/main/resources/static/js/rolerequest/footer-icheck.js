/**
 * Initializes iCheck styling on any .i-checks elements present on the page.
 * Only loaded when the footer fragment is included with checkbox = true.
 *
 * window.skipAutoICheckInit lets a page opt out of the automatic init (e.g. if
 * it wants to initialize iCheck itself with custom options), same as before.
 */
document.addEventListener("DOMContentLoaded", () => {
    if (!window.skipAutoICheckInit) {
        $('.i-checks').iCheck({
            checkboxClass: 'icheckbox_square-green',
            radioClass: 'iradio_square-green'
        });
    }
});
