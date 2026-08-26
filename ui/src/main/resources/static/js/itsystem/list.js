const itsystemListConfig = JSON.parse(document.getElementById("itsystem-list-config").textContent);
const token = $("meta[name='_csrf']").attr("content");
const sweetAlertService = new SweetAlertService();

$(document).ready(() => {
    const listTable = $("#listTable").DataTable();

    $("input[name=showHidden]").change(function () {
        listTable.column(0).search(this.checked).draw();
    });

    // Default: only show visible (non-hidden) rows
    listTable.column(0).search(false).draw();
});

$(document).on("click", ".js-open-delete-confirm", function () {
    const id = $(this).data("id");

    sweetAlertService.confirm(
        itsystemListConfig.titleTxt,
        itsystemListConfig.bodyTxt,
        itsystemListConfig.confirmTxt,
        itsystemListConfig.cancelTxt,
        () => {
            $.ajax({
                method: "POST",
                headers: {
                    "X-CSRF-TOKEN": token
                },
                url: itsystemListConfig.deleteUri + id,
                success: () => {
                    window.location.href = itsystemListConfig.urlList;
                }
            });
        }
    );
});
