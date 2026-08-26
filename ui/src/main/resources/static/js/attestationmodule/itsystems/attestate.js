/**
 * Handles the IT system attestation page: paging through user roles, and the
 * approve / reject flow for each role via the shared remark modal.
 */
class UserRoleService {
    constructor(config) {
        this.config = config;
    }

    init() {
        $(".userRoleRow").first().show();
        $(".btn-back").click(this.handleBack);
        $(".btn-forth").click(this.handleForth);
        $(".approveBtn").click(this.handleApprove);
        $(".readCheckbox").on('ifChanged', this.handleReadCheckbox);
        $("#modalSaveBtn").click(this.handleReject);

        $('#remarkModal').on('show.bs.modal', (event) => {
            const id = $(event.relatedTarget).data('id');
            const number = $(event.relatedTarget).data('number');
            $("#idInput").val(id);
            $("#numberInput").val(number);
            $("#remarkError").hide();
            $("#remarkField").val("");
        });

        $('.table').DataTable({
            "bSort": false,
            "paging": false,
            "responsive": true,
            "dom": "<'row'<'col-sm-12'tr>>",
            "language": {
                "search": "Søg",
                "lengthMenu": "_MENU_ rækker per side",
                "info": "Viser _START_ til _END_ af _TOTAL_ rækker",
                "zeroRecords": "Ingen data...",
                "infoEmpty": "",
                "infoFiltered": "(ud af _MAX_ rækker)",
                "paginate": {
                    "previous": "Forrige",
                    "next": "Næste"
                }
            }
        });
    }

    handleBack = (event) => {
        const number = $(event.currentTarget).data("number");
        const minusOne = number - 1;
        $("#userRoleRow" + number).first().hide();
        $("#userRoleRow" + minusOne).first().show();
    }

    handleForth = (event) => {
        const number = $(event.currentTarget).data("number");
        const plusOne = number + 1;
        $("#userRoleRow" + number).first().hide();
        $("#userRoleRow" + plusOne).first().show();
    }

    handleApprove = (event) => {
        const button = $(event.currentTarget);
        const userRoleId = button.data("id");
        const number = button.data("number");
        const plusOne = number + 1;

        $.ajax({
            url: this.config.restUrl + "/" + this.config.itSystemId + "/userroles/" + userRoleId + "/approve",
            method: 'POST',
            headers: {
                'X-CSRF-TOKEN': window.token
            },
            success: () => {
                $("#approveBtn" + userRoleId).hide();
                $("#rejectBtn" + userRoleId).hide();
                $("#alreadyDone" + userRoleId).show();
                $("#checkboxRow" + userRoleId).hide();

                // change to next page if any next page
                if (plusOne != this.config.totalCount) {
                    $("#userRoleRow" + number).first().hide();
                    $("#userRoleRow" + plusOne).first().show();
                }
            },
            error: defaultErrorHandler
        });
    }

    handleReadCheckbox = (event) => {
        const checkbox = $(event.currentTarget);
        const id = checkbox.data("id");
        const checked = checkbox.prop("checked");

        if (checked) {
            $("#approveBtn" + id).attr("disabled", false);
        } else {
            $("#approveBtn" + id).attr("disabled", true);
        }
    }

    handleReject = () => {
        const userRoleId = $("#idInput").val();
        const remark = $("#remarkField").val();
        const numberString = $("#numberInput").val();
        const number = parseInt(numberString);
        const plusOne = number + 1;

        $.ajax({
            url: this.config.restUrl + "/" + this.config.itSystemId + "/userroles/" + userRoleId + "/reject",
            headers: {
                "content-type": "application/json",
                'X-CSRF-TOKEN': window.token
            },
            method: 'POST',
            data: remark,
            success: () => {
                $("#approveBtn" + userRoleId).hide();
                $("#rejectBtn" + userRoleId).hide();
                $("#alreadyDone" + userRoleId).show();
                $('#remarkModal').modal('toggle');
                $("#remarkRow" + userRoleId).show();
                $("#remarkPre" + userRoleId).text(remark);
                $("#checkboxRow" + userRoleId).hide();

                // change to next page if any next page
                if (plusOne != this.config.totalCount) {
                    $("#userRoleRow" + number).first().hide();
                    $("#userRoleRow" + plusOne).first().show();
                }
            },
            error: (jqXHR) => {
                defaultErrorHandler(jqXHR);
                $("#remarkError").show();
            }
        });
    }
}

$(document).ready(function () {
    const config = JSON.parse(document.getElementById('itSystemsAttestConfig').textContent);
    window.token = $("meta[name='_csrf']").attr("content");

    window.userRoleService = new UserRoleService(config);
    window.userRoleService.init();
});
