/**
 * Bootstrap for the "fragments/kle :: kleEdit" fragment.
 *
 * This fragment is always loaded dynamically via KleService.loadEditFragment(),
 * using jQuery's .load(), which re-evaluates every <script> tag in the response
 * each time. That means this file runs again on every edit/cancel/edit cycle.
 *
 * Config handling therefore has to run every time (to refresh window.kle*Selected
 * with the freshly rendered list), while the click listener must only be attached
 * once - otherwise repeated loads would stack up duplicate listeners and each
 * click would trigger saveChanges/abortChanges multiple times.
 *
 * window.kleService is expected to already exist - it is created by each
 * consuming page's own bootstrap script (e.g. the OU management page), since
 * its configuration (URLs, all available KLE codes) is page-specific.
 */
function initKleEditFragment() {
    document.querySelectorAll(".js-kle-edit-config").forEach((configElement) => {
        if (configElement.dataset.processed === "true") {
            return;
        }
        configElement.dataset.processed = "true";

        const config = JSON.parse(configElement.textContent);
        window[`kle${config.type}Selected`] = config.selectedKles;
    });

    if (window.kleEditListenerAttached) {
        return;
    }
    window.kleEditListenerAttached = true;

    document.addEventListener("click", (event) => {
        const saveButton = event.target.closest(".js-kle-save");
        if (saveButton) {
            window.kleService.saveChanges(saveButton);
            return;
        }

        const cancelButton = event.target.closest(".js-kle-cancel");
        if (cancelButton) {
            window.kleService.abortChanges(cancelButton);
        }
    });
}

initKleEditFragment();
