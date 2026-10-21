/**
 * Wires up the fragments/saveHeader.html fragment: toggles the unsaved-changes
 * indicator, and gives the Cancel button a default "discard and reload" behaviour.
 */
document.addEventListener("DOMContentLoaded", () => {
    const header = document.querySelector(".js-save-header");
    if (!header) {
        return;
    }

    const actions = header.querySelector(".save-header-actions");
    const watchRoot = header.closest(".content-wrapper") || document;

    window.unsavedChangesService = new UnsavedChangesService(watchRoot, (dirty) => {
        actions.classList.toggle("is-dirty", dirty);
    });

    const cancelBtn = header.querySelector(".js-page-cancel");
    if (cancelBtn) {
        cancelBtn.addEventListener("click", () => {
            window.unsavedChangesService.reset();
            window.location.reload();
        });
    }

    const saveBtn = header.querySelector(".js-page-save");
    if (saveBtn) {
        const formId = saveBtn.getAttribute("form");
        if (formId) {
            const form = document.getElementById(formId);
            if (form) {
                form.addEventListener("submit", () => window.unsavedChangesService.reset());
            }
        }
    }
});
