/**
 * Service class encapsulating the "delete duplicate assignments" flow:
 * confirm dialog -> notify -> spinner overlay -> POST cleanup request -> notify/reload.
 * Shared by the duplicate role group and duplicate user role cleanup report pages.
 */
class DuplicateCleanupService {
    /**
     * @param {Object} config
     * @param {string} config.url cleanup endpoint URL
     * @param {string} config.txtTitle confirm dialog title
     * @param {string} config.txtBody confirm dialog body text
     * @param {string} config.txtOrderConfirmed notify message shown right after confirming
     * @param {string} config.txtCleanupFailed notify message passed to errorHandler on failure
     * @param {string} config.txtCleanupSucceeded notify message shown on success
     * @param {string} [config.txtCleanupAlreadyRunning] notify message shown when the server answers 409 because another clean-up is running
     * @param {string} config.txtQueueSpinner message shown in the spinner overlay while draining
     * @param {string} config.btnNo cancel button text
     * @param {string} config.btnYes confirm button text
     * @param {string} config.panelSelector CSS selector for the panel the spinner overlay is shown on
     */
    constructor(config) {
        this.url = config.url;
        this.txtTitle = config.txtTitle;
        this.txtBody = config.txtBody;
        this.txtOrderConfirmed = config.txtOrderConfirmed;
        this.txtCleanupFailed = config.txtCleanupFailed;
        this.txtCleanupSucceeded = config.txtCleanupSucceeded;
        this.txtCleanupAlreadyRunning = config.txtCleanupAlreadyRunning;
        this.txtQueueSpinner = config.txtQueueSpinner;
        this.btnNo = config.btnNo;
        this.btnYes = config.btnYes;
        this.panelSelector = config.panelSelector;

        this.sweetAlertService = new SweetAlertService();
    }

    deleteDuplicates() {
        const self = this;

        this.sweetAlertService.confirm(
            this.txtTitle,
            this.txtBody,
            this.btnYes,
            this.btnNo,
            () => {
                $.notify({ message: self.txtOrderConfirmed }, { status: 'success', autoHideDelay: 4000 });

                const spinner = new QueueDrainService(self.panelSelector, { message: self.txtQueueSpinner });
                spinner.show();

                $.ajax({
                    url: self.url,
                    method: "POST",
                    headers: {
                        'X-CSRF-TOKEN': window.token
                    },
                    error: function (jqXHR) {
                        spinner.stop();
                        if (jqXHR.status === 409 && self.txtCleanupAlreadyRunning) {
                            $.notify({ message: self.txtCleanupAlreadyRunning }, { status: 'warning', autoHideDelay: 4000 });
                            return;
                        }
                        errorHandler(self.txtCleanupFailed).apply(this, arguments);
                    },
                    success: function () {
                        $.notify({ message: self.txtCleanupSucceeded }, { status: 'success', autoHideDelay: 4000 });
                        spinner.watch(() => {
                            location.reload(true);
                        });
                    }
                });
            }
        );
    }
}
