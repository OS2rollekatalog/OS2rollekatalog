/**
 * Tracks whether a page has unsaved changes, and warns the user with the
 * browser's native "Leave site?" prompt if they try to navigate away while dirty.
 */
class UnsavedChangesService {
    #dirty = false;
    #onChange;

    /**
     * @param {HTMLElement|Document} root Element to watch for input/change events (delegated)
     * @param {(dirty: boolean) => void} onChange Called whenever the dirty state changes
     */
    constructor(root = document, onChange = () => {}) {
        this.#onChange = onChange;

        root.addEventListener("input", () => this.setChangesMade());
        root.addEventListener("change", () => this.setChangesMade());

        window.addEventListener("beforeunload", (event) => {
            if (this.#dirty) {
                event.preventDefault();
            }
        });
    }

    setChangesMade() {
        if (!this.#dirty) {
            this.#dirty = true;
            this.#onChange(true);
        }
    }

    reset() {
        if (this.#dirty) {
            this.#dirty = false;
            this.#onChange(false);
        }
    }

    isDirty() {
        return this.#dirty;
    }
}

// No-op default so page-specific scripts can call window.unsavedChangesService
// safely even on pages with no .js-save-header
// (SaveHeader.js replaces this with a real instance when one is present).
window.unsavedChangesService = {
    setChangesMade() {},
    reset() {},
    isDirty() {
        return false;
    }
};
