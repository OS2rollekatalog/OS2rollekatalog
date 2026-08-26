/**
 * Reads the per-type JSON config blocks rendered inline by the
 * "fragments/kle :: kleEdit" fragment (id "kle-edit-<type>-config").
 *
 * Used by both the user-manage KLE tab (manageKle.js) and the OU KLE tab
 * (KleService.js), which each consume the same fragment but otherwise have
 * distinct KleService implementations.
 */
class KleEditConfigService {
    constructor() {}

    /**
     * Reads the selected KLE codes for the given type ("PERFORMING"/"INTEREST").
     *
     * Must be read here rather than via a global variable set by an externally-src'd
     * script: this is called inside jQuery's .load() success callback, which fires
     * as soon as the fragment's HTML is inserted - before any <script src="..."> in
     * that HTML is guaranteed to have finished loading. The config block is inert
     * markup (type="application/json"), so it's present in the DOM the instant
     * .load() finishes inserting the fragment - no such race applies to it.
     * @param {string} type "PERFORMING" or "INTEREST"
     * @returns {Array} the selected KLE codes
     */
    readSelectedKles(type) {
        const configElement = document.getElementById(`kle-edit-${type}-config`);
        return JSON.parse(configElement.textContent).selectedKles;
    }
}

window.kleEditConfigService = window.kleEditConfigService || new KleEditConfigService();
