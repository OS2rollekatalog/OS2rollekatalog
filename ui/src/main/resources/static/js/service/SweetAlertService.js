/**
 * Service class for the SweetAlert dialog plugin
 */
if (typeof SweetAlertService === 'undefined') {
    /**
     * Service class for the SweetAlert dialog plugin
     */
    class SweetAlertService {
        defaultConfirmConfig = {
            html: true,
            type: "warning",
            showCancelButton: true,
            confirmButtonColor: "#DD6B55",
            closeOnConfirm: true,
            closeOnCancel: true
        }

        constructor() {}

        /**
         * Shows a confirmation dialog and invokes onConfirm or onCancel depending on the user's choice
         * @param {string} title
         * @param {string} text
         * @param {string} confirmButtonText
         * @param {string} cancelButtonText
         * @param {Function} onConfirm callback invoked when the user confirms
         * @param {Object} options (optional)
         * @param {Function} options.onCancel callback invoked when the user cancels or dismisses the dialog
         * @param {Object} options.customConfig overrides merged on top of defaultConfirmConfig
         */
        confirm(title, text, confirmButtonText, cancelButtonText, onConfirm, options = {}) {
            const { onCancel, customConfig } = options;

            const config = {
                ...this.defaultConfirmConfig,
                ...customConfig,
                title,
                text,
                confirmButtonText,
                cancelButtonText
            };

            swal(config, function (isConfirm) {
                if (isConfirm) {
                    onConfirm();
                } else if (onCancel) {
                    onCancel();
                }
            });
        }
    }

    window.SweetAlertService = SweetAlertService;
}

// Shared singleton, since SweetAlertService is stateless and used by many fragments and pages.
window.sweetAlertService = window.sweetAlertService || new window.SweetAlertService();
