/**
 * Handles the front page links admin page: creating, editing, deleting,
 * reordering, and toggling active state for links shown per LinkType.
 */
document.addEventListener("DOMContentLoaded", () => {
    const config = JSON.parse(document.getElementById("front-page-links-config").textContent);
    const token = document.querySelector("meta[name='_csrf']").getAttribute("content");

    const linkService = new LinkService(config, token);
    linkService.init();
});

class LinkService {
    constructor(config, token) {
        this.config = config;
        this.token = token;
        this.sweetAlertService = new SweetAlertService();
    }

    init() {
        $("#createBtn").on("click", () => this.openCreateModal());
        $(".editBtn").on("click", (event) => this.openEditModal(event.currentTarget));
        $(".linkActiveCheckbox").on("change", (event) => this.setActive(event.currentTarget));
        $(".removeBtn").on("click", (event) => this.delete(event.currentTarget));
        $("#viewBtn").on("click", () => this.openViewModal());
        $("#saveLinkBtn").on("click", () => this.save());
        $(".moveUpBtn").on("click", (event) => this.moveUp(event.currentTarget));
        $(".moveDownBtn").on("click", (event) => this.moveDown(event.currentTarget));

        // Hide inactive links in view modal
        Object.values(this.config.linksByType).flat().forEach((link) => {
            if (!link.active) {
                $("#viewLink" + link.id).hide();
            }
        });
    }

    openViewModal() {
        $("#linkViewModal").modal("show");
    }

    openCreateModal() {
        $("#selectedLinkId").val("");
        $("#linkTitle").val("");
        $("#linkLink").val("");
        $("#linkDescription").text("");
        $("#modalHeader").text(this.config.modalCreateHeader);
        $("#linkModal").modal("show");
    }

    openEditModal(element) {
        const id = $(element).data("id");
        const linkType = $(element).data("type");
        let link = null;

        const linksForType = this.config.linksByType[linkType];
        if (linksForType) {
            linksForType.forEach((item) => {
                if (item.id === id) {
                    link = item;
                }
            });
        }

        if (link !== null) {
            $("#selectedLinkId").val(id);
            $("#linkTitle").val(link.title);
            $("#linkLink").val(link.link);
            $("#linkIcon").selectpicker("val", link.icon);
            $("#linkDescription").text(link.description);
            $("#modalHeader").text(this.config.modalEditHeader);
            $("#linkType").selectpicker("val", link.linkType);
            $("#linkModal").modal("show");
        } else {
            this.openCreateModal();
        }
    }

    moveUp(element) {
        const id = $(element).data("id");
        $.ajax({
            method: "POST",
            url: this.config.restUrl + id + "/moveUp",
            contentType: "application/json; charset=utf-8",
            headers: {
                "X-CSRF-TOKEN": this.token
            }
        }).done(() => {
            location.reload(true);
        }).fail(errorHandler(this.config.msgError));
    }

    moveDown(element) {
        const id = $(element).data("id");
        $.ajax({
            method: "POST",
            url: this.config.restUrl + id + "/moveDown",
            contentType: "application/json; charset=utf-8",
            headers: {
                "X-CSRF-TOKEN": this.token
            }
        }).done(() => {
            location.reload(true);
        }).fail(errorHandler(this.config.msgError));
    }

    delete(element) {
        const id = $(element).data("id");
        const title = $(element).data("title");

        this.sweetAlertService.confirm(
            this.config.deleteTitle,
            this.config.deleteText + "<br/><br/><b><q>" + title + "</q></b>",
            this.config.deleteConfirmBtn,
            this.config.deleteCancelBtn,
            () => {
                $.ajax({
                    method: "POST",
                    url: this.config.restUrl + id + "/delete",
                    contentType: "application/json; charset=utf-8",
                    headers: {
                        "X-CSRF-TOKEN": this.token
                    }
                }).done(() => {
                    location.reload(true);
                }).fail(errorHandler(this.config.msgError));
            }
        );
    }

    setActive(element) {
        const checked = $(element).prop("checked");
        const id = $(element).data("id");

        $.ajax({
            method: "POST",
            url: this.config.restUrl + id + "?active=" + checked,
            contentType: "application/json; charset=utf-8",
            headers: {
                "X-CSRF-TOKEN": this.token
            }
        }).done(() => {
            if (checked) {
                $("#viewLink" + id).show();
            } else {
                $("#viewLink" + id).hide();
            }

            $.notify({
                message: this.config.msgSuccess
            }, {
                status: "success",
                autoHideDelay: 4000
            });
        }).fail(errorHandler(this.config.msgError));
    }

    save() {
        const id = $("#selectedLinkId").val();
        const title = $("#linkTitle").val();
        const link = $("#linkLink").val();
        const icon = $("#linkIcon").val();
        const description = $("#linkDescription").val();
        const type = $("#linkType").val();

        $.ajax({
            method: "POST",
            url: this.config.restUrl + "save",
            contentType: "application/json; charset=utf-8",
            headers: {
                "X-CSRF-TOKEN": this.token
            },
            data: JSON.stringify({
                id: id,
                title: title,
                link: link,
                icon: icon,
                description: description,
                linkType: type
            })
        }).done(() => {
            location.reload(true);
        }).fail((jqXHR) => {
            $("#linkModal").modal("hide");
            errorHandler(this.config.msgError)(jqXHR);
        });
    }
}
