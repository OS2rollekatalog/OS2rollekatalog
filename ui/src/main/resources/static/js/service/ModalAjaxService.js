/**
 * Shared AJAX helper for the role-assignment modals: builds a standard
 * options object with CSRF header and success/error notifications.
 *
 * Guarded against multiple inclusions: several self-contained fragments
 * (user role modal, role group modal, edit modal) each load this file
 * independently, and can end up on the same page together.
 */
if (typeof ModalAjaxService === 'undefined') {
    class ModalAjaxService {
        constructor() {}

        getAjaxObject(url, okMsg, errorMsg) {
            return {
                url,
                method: "POST",
                headers: {
                    'X-CSRF-TOKEN': window.token
                },
                error: (response) => {
                    const msg = (response.responseText != null && response.responseText.length > 0) ? response.responseText : errorMsg;
                    this.showErrorNotification(msg);
                },
                success: () => {
                    this.showInfoNotification(okMsg);
                }
            };
        }

        showInfoNotification(message) {
            $.notify({
                message,
                status: 'success',
                timeout: 2000
            });
        }

        showErrorNotification(message) {
            $.notify({
                message,
                status: 'danger',
                timeout: 4000
            });
        }
    }

    window.ModalAjaxService = ModalAjaxService;
}
