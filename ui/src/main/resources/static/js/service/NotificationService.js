/**
 * Service class for showing toast-style notifications via $.notify
 */
if (typeof NotificationService === 'undefined') {
    /**
     * Service class for showing toast-style notifications via $.notify
     */
    class NotificationService {
        constructor() {}

        showInfoNotification(message) {
            $.notify({
                message: message,
                status: 'success',
                timeout: 2000
            });
        }

        showWarnNotification(message) {
            $.notify({
                message: message,
                status: 'warning',
                timeout: 3000
            });
        }

        showErrorNotification(message) {
            $.notify({
                message: message,
                status: 'danger',
                timeout: 4000
            });
        }
    }

    window.NotificationService = NotificationService;
}

// Shared singleton, since NotificationService is stateless and used by many fragments and
// pages. Guarded with `||` so loading this script more than once (e.g. via multiple included
// fragments on the same page) never overwrites an already-created instance.
window.notificationService = window.notificationService || new window.NotificationService();
