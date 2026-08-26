/**
 * Handles the "users" tab: paging, and approve / reject flow (via the shared
 * user remark modal) for each user's role assignments under this IT system.
 */
class UserService {
    constructor(config) {
        this.config = config;
    }

    init() {
        $(".userRoleRow").first().show();
        $(".btn-back").click(this.handleBack);
        $(".btn-forth").click(this.handleForth);
        $(".approveBtn").click(this.handleApprove);
        $(".rejectBtn").click(this.openModal);
        $(".readCheckbox").on('ifChanged', this.handleReadCheckbox);
        $("#modalSaveBtn").click(this.handleReject);

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

    openModal = (event) => {
        const button = $(event.currentTarget);
        const uuid = button.data("uuid");
        const number = button.data("number");

        $("#userRemarkModalPlaceholder").load(this.config.uiUrl + this.config.attestationUuid + "/users/" + uuid + "/userRemarkFragment?number=" + number, () => {
            $('#userRemarkModal').modal('toggle');
            window.userRemarkModalService.init();
            $("#modalSaveBtn").click(this.handleReject);
        });
    }

    handleBack = (event) => {
        const number = $(event.currentTarget).data("number");
        const minusOne = number - 1;
        $("#userRow" + number).first().hide();
        $("#userRow" + minusOne).first().show();
    }

    handleForth = (event) => {
        const number = $(event.currentTarget).data("number");
        const plusOne = number + 1;
        $("#userRow" + number).first().hide();
        $("#userRow" + plusOne).first().show();
    }

    handleApprove = (event) => {
        const button = $(event.currentTarget);
        const userUuid = button.data("uuid");
        const number = button.data("number");

        $.ajax({
            url: this.config.restUrl + "/" + this.config.itSystemId + "/users/" + userUuid + "/approve",
            method: 'POST',
            headers: {
                'X-CSRF-TOKEN': window.token
            },
            success: () => {
                $("#approveBtn" + userUuid).hide();
                $("#rejectBtn" + userUuid).hide();
                $("#alreadyDone" + userUuid).show();
                $("#checkboxRow" + userUuid).hide();

                this.handleBadgeAndPageSwitch(userUuid, number);
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
        // Validate that at least one role is selected
        if (!window.userRemarkModalService.validateSelection()) {
            return;
        }

        const notApproved = window.userRemarkModalService.getNotApprovedRoles();
        const userUuid = $("#userUuidInput").val();
        const remark = $("#userRemarkField").val();
        const numberString = $("#userNumberInput").val();
        const number = parseInt(numberString);
        const body = {
            "remarks": remark,
            "notApproved": notApproved
        };

        $.ajax({
            url: this.config.restUrl + "/" + this.config.itSystemId + "/users/" + userUuid + "/reject",
            headers: {
                "content-type": "application/json",
                'X-CSRF-TOKEN': window.token
            },
            method: 'POST',
            data: JSON.stringify(body),
            success: () => {
                $("#approveBtn" + userUuid).hide();
                $("#rejectBtn" + userUuid).hide();
                $("#alreadyDone" + userUuid).show();
                $('#userRemarkModal').modal('toggle');
                $("#remarkRow" + userUuid).show();
                $("#remarkPre" + userUuid).text(remark);
                $("#checkboxRow" + userUuid).hide();

                this.handleBadgeAndPageSwitch(userUuid, number);
            },
            error: defaultErrorHandler
        });
    }

    handleBadgeAndPageSwitch(userId, number) {
        // update badges
        const userCount = $("#badgeUsersText").text();
        const userCountInt = parseInt(userCount);
        if (userCountInt > 1) {
            $("#badgeUsersText").text(userCountInt - 1);
        } else {
            $("#badgeUsers").hide();
        }

        // change to next page if any next page
        const plusOne = number + 1;
        if (plusOne != this.config.totalCount) {
            $("#userRow" + number).first().hide();
            $("#userRow" + plusOne).first().show();
        } else if (this.config.orgUnitTotalCount > 0) {
            // change to orgUnitPane
            $(".mainTabs").removeClass('active');
            $("#ouTab").addClass('active');
            $("#orgUnitsTab").addClass('active');
        } else {
            window.location.href = "/ui/attestation/v2";
        }

        $("html, body").animate({ scrollTop: 0 }, "slow");
    }
}

/**
 * Handles the "org units" tab: paging, and approve / reject flow (via the
 * page's own embedded remark modal) for each org unit's role assignments
 * under this IT system.
 */
class OrgUnitService {
    constructor(config) {
        this.config = config;
    }

    init() {
        $(".orgUnitRow").first().show();
        $(".orgUnit-btn-back").click(this.handleBack);
        $(".orgUnit-btn-forth").click(this.handleForth);
        $(".approveOrgUnitBtn").click(this.handleApprove);
        $(".rejectOrgUnitBtn").click(this.openModal);
        $(".orgUnitReadCheckbox").on('ifChanged', this.handleReadCheckbox);
        $("#modalSaveOrgUnitBtn").click(this.handleReject);
    }

    handleReadCheckbox = (event) => {
        const checkbox = $(event.currentTarget);
        const id = checkbox.data("id");
        const checked = checkbox.prop("checked");

        if (checked) {
            $("#approveOrgUnitBtn" + id).attr("disabled", false);
        } else {
            $("#approveOrgUnitBtn" + id).attr("disabled", true);
        }
    }

    handleBack = (event) => {
        const number = $(event.currentTarget).data("number");
        const minusOne = number - 1;
        $("#orgUnitRow" + number).first().hide();
        $("#orgUnitRow" + minusOne).first().show();
    }

    handleForth = (event) => {
        const number = $(event.currentTarget).data("number");
        const plusOne = number + 1;
        $("#orgUnitRow" + number).first().hide();
        $("#orgUnitRow" + plusOne).first().show();
    }

    openModal = (event) => {
        const button = $(event.currentTarget);
        const id = button.data("uuid");
        const number = button.data("number");

        $("#idInput").val(id);
        $("#numberInput").val(number);
        $("#remarkError").hide();
        $("#remarkField").val("");
        $("#modalSaveOrgUnitBtn").show();
        $('#remarkModal').modal('toggle');
    }

    handleApprove = (event) => {
        const button = $(event.currentTarget);
        const ouUuid = button.data("uuid");
        const numberString = button.data("number");
        const number = parseInt(numberString);

        $.ajax({
            url: this.config.restUrl + "/" + this.config.itSystemId + "/orgunits/" + ouUuid + "/approve",
            method: 'POST',
            headers: {
                'X-CSRF-TOKEN': window.token
            },
            success: () => {
                $("#approveOrgUnitBtn" + ouUuid).hide();
                $("#rejectOrgUnitBtn" + ouUuid).hide();
                $("#alreadyDoneOrgUnit" + ouUuid).show();
                $("#orgUnitheckboxRow" + ouUuid).hide();

                this.handleBadgeAndPageSwitch(number);
            },
            error: defaultErrorHandler
        });
    }

    handleReject = () => {
        const remark = $("#remarkField").val();
        const ouUuid = $("#idInput").val();
        const numberString = $("#numberInput").val();
        const number = parseInt(numberString);

        $.ajax({
            url: this.config.restUrl + "/" + this.config.itSystemId + "/orgunits/" + ouUuid + "/reject",
            headers: {
                "content-type": "application/json",
                'X-CSRF-TOKEN': window.token
            },
            method: 'POST',
            data: remark == "" ? " " : remark,
            success: () => {
                $("#approveOrgUnitBtn" + ouUuid).hide();
                $("#rejectOrgUnitBtn" + ouUuid).hide();
                $("#alreadyDoneOrgUnit" + ouUuid).show();
                $('#remarkModal').modal('toggle');
                $("#orgUnitRemarkRow" + ouUuid).show();
                $("#orgUnitRemarkPre" + ouUuid).text(remark);

                this.handleBadgeAndPageSwitch(number);
            },
            error: (jqXHR) => {
                defaultErrorHandler(jqXHR);
                $("#remarkError").show();
            }
        });
    }

    handleBadgeAndPageSwitch(number) {
        // update badges
        const orgUnitCount = $("#badgeOrgUnitsText").text();
        const orgUnitCountInt = parseInt(orgUnitCount);
        if (orgUnitCountInt > 1) {
            $("#badgeOrgUnitsText").text(orgUnitCountInt - 1);
        } else {
            $("#badgeOrgUnits").hide();
        }

        // change to next page if any next page, otherwise redirect to overview
        const plusOne = number + 1;
        if (plusOne != this.config.orgUnitTotalCount) {
            $("#orgUnitRow" + number).first().hide();
            $("#orgUnitRow" + plusOne).first().show();
        } else {
            window.location.href = "/ui/attestation/v2";
        }

        $("html, body").animate({ scrollTop: 0 }, "slow");
    }
}

$(document).ready(function () {
    const config = JSON.parse(document.getElementById('itSystemsUsersAttestConfig').textContent);
    window.token = $("meta[name='_csrf']").attr("content");

    window.userService = new UserService(config);
    window.userService.init();

    window.orgUnitService = new OrgUnitService(config);
    window.orgUnitService.init();
});
