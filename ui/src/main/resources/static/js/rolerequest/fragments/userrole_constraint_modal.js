/**
 * Delegated listeners for the user-role constraint modal (shared fragment).
 * Relies on window.constraintService.onModalConfirm()/onModalCancel() being
 * set up by whichever page includes this fragment (currently only the
 * request wizard).
 *
 * This fragment is loaded dynamically into the DOM via ConstraintService's
 * loadModal() (a jQuery .load() call), so it isn't present at DOMContentLoaded
 * time - the listener must be delegated on document, which exists immediately
 * when this script runs, rather than bound directly to the buttons.
 *
 * Guarded against multiple inclusions, since loadModal() re-fetches and
 * re-inserts this fragment (and its script tag) every time a constrained
 * checkbox is checked, which would otherwise register a new click listener
 * on every single load and cause the confirm/cancel actions to fire more
 * than once per click.
 */
if (!window.userroleConstraintModalListenerAttached) {
    window.userroleConstraintModalListenerAttached = true;

    document.addEventListener("click", (event) => {
        if (event.target.closest(".js-confirm-userrole-constraint")) {
            window.constraintService.onModalConfirm();
            return;
        }

        if (event.target.closest(".js-cancel-userrole-constraint")) {
            window.constraintService.onModalCancel();
        }
    });
}
