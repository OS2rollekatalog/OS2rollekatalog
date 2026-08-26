// Handles the report template list page (report/list_template.html)
class ReportTemplateListService {
    constructor() {
        const config = JSON.parse(document.getElementById("report-template-list-config").textContent);
        this.url = config.url;
        this.restUrl = config.restUrl;
        this.titleTxt = config.titleTxt;
        this.bodyTxt = config.bodyTxt;
        this.cancelTxt = config.cancelTxt;
        this.confirmTxt = config.confirmTxt;
        this.sweetAlertService = new SweetAlertService();
    }

    init() {
        document.addEventListener("click", (event) => {
            const deleteLink = event.target.closest(".js-delete-report-template");
            if (deleteLink) {
                event.preventDefault();
                this.openConfirmDeleteDialog(deleteLink);
            }
        });
    }

    openConfirmDeleteDialog(elem) {
        const id = $(elem).data("id");

        this.sweetAlertService.confirm(
            this.titleTxt,
            this.bodyTxt,
            this.confirmTxt,
            this.cancelTxt,
            () => {
                $.ajax({
                    method: "POST",
                    headers: {
                        "X-CSRF-TOKEN": window.token
                    },
                    url: `${this.restUrl}/delete-template/${id}`,
                    success: () => {
                        window.location.href = `${this.url}/templates`;
                    },
                    error: defaultErrorHandler
                });
            }
        );
    }
}

document.addEventListener("DOMContentLoaded", () => {
    window.token = $("meta[name='_csrf']").attr("content");
    window.reportTemplateListService = new ReportTemplateListService();
    window.reportTemplateListService.init();
});
