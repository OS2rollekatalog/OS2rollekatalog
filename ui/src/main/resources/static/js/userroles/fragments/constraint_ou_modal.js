/**
 * Delegated listeners for the OU constraint modal (shared fragment).
 * Relies on window.orgUnitPostponedConstraintService and window.orgUnitConstraintService
 * being set up by whichever page includes this fragment.
 *
 * No DOMContentLoaded wrapper: the listener is delegated on document, which exists
 * immediately when this script runs - avoiding any race against a page's own
 * (possibly not-yet-refactored) ready handler.
 *
 * Guarded against multiple inclusions, since several pages may include this
 * fragment and each would otherwise register its own click listener, causing
 * the save actions to fire more than once per click.
 */
if (!window.ouModalListenerAttached) {
    window.ouModalListenerAttached = true;

    document.addEventListener("click", (event) => {
        if (event.target.closest(".postponed-constraint-save-btn")) {
            window.orgUnitPostponedConstraintService.oUModalSaveConstraints();
            return;
        }

        if (event.target.closest("#ou-modal-save-default")) {
            window.orgUnitConstraintService.oUModalSaveConstraints();
            return;
        }

        if (event.target.closest("#ou-modal-save-inherited")) {
            window.orgUnitConstraintService.oUModalSaveConstraintsWithInheritance();
        }
    });
}
