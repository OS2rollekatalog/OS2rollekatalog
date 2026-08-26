/**
 * Delegated listener for the removal-request confirmation modal (shared fragment).
 * Relies on window.requestService.onRemovalModalConfirm() being set up by whichever
 * page includes this fragment.
 *
 * No DOMContentLoaded wrapper: the listener is delegated on document, which exists
 * immediately, so it can attach as soon as this script runs - avoiding any race
 * against a page's own (possibly not-yet-refactored) ready handler.
 *
 * Guarded against multiple inclusions, since several pages may include this
 * fragment and each would otherwise register its own click listener, causing the
 * confirm action to fire more than once per click.
 */
if (!window.requestRemovalModalListenerAttached) {
	window.requestRemovalModalListenerAttached = true;

	document.addEventListener("click", (event) => {
		const confirmButton = event.target.closest(".js-confirm-removal");
		if (confirmButton) {
			window.requestService.onRemovalModalConfirm();
		}
	});
}
