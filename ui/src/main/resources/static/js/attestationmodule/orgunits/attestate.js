/**
 * Handles the "history" tab table for the org unit attestation page.
 */
class HistoryService {
    constructor() {}

    init() {
        $('.historyTable').DataTable({
            "pageLength": 50,
            "responsive": true,
            "autoWidth": false,
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
}

/**
 * General page utilities not specific to users or org units: the role
 * summary table, and the expand/collapse chevrons.
 */
class UtilService {
    constructor() {}

    init() {
        $('.roleTable').DataTable({
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

        $(".expandable").click(this.handleExpandableClick);
    }

    // Arrow function class field so 'this' always refers to the UtilService
    // instance if ever needed, while the clicked element is read via event.currentTarget.
    handleExpandableClick = (event) => {
        const element = $(event.currentTarget);
        element.toggleClass("active");
        const sibling = element.next().slideToggle(200);
        const firstITag = element.find("i:first");
        firstITag.toggleClass("fa-chevron-down");
        firstITag.toggleClass("fa-chevron-up");
    }
}

/**
 * Handles the "users" tab: paging through users, and the approve / reject /
 * request-delete flows for each user's role assignments.
 */
class UserService {
    constructor(config) {
        this.config = config;
    }

    init() {
        $(".userRow").first().show();
        $(".btn-back").click(this.handleBack);
        $(".btn-forth").click(this.handleForth);
        $(".approveBtn").click(this.handleApprove);
        $(".requestDeleteBtn").click(this.handleRequestDelete);
        $(".rejectBtn").click(this.openModal);
        $(".readCheckbox").on('ifChanged', this.handleReadCheckbox);
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
        const userId = button.data("uuid");
        const number = button.data("number");
        const managerdelegate = button.data("managerdelegate");

        $.ajax({
            url: this.config.restUrl + "/" + this.config.orgUnitUuid + "/users/" + userId + "/approve" + "?managerdelegate=" + (managerdelegate || "false"),
            method: 'POST',
            headers: {
                'X-CSRF-TOKEN': window.token
            },
            success: () => {
                $("#approveBtn" + userId).hide();
                $("#rejectBtn" + userId).hide();
                $("#requestDeleteBtn" + userId).hide();
                $("#alreadyDone" + userId).show();
                this.handleBadgeAndPageSwitch(userId, number);
            },
            error: defaultErrorHandler
        });
    }

    handleReject = (event) => {
        // Validate that at least one role is selected
        if (!window.userRemarkModalService.validateSelection()) {
            return;
        }

        const notApproved = window.userRemarkModalService.getNotApprovedRoles();
        const userUuid = $("#userUuidInput").val();
        const numberString = $("#userNumberInput").val();
        const number = parseInt(numberString);

        let remark = null;
        if (!this.config.hideDescription) {
            remark = $("#userRemarkField").val();
        }

        const managerdelegate = $(event.currentTarget).data("managerdelegate");
        const body = {
            "remarks": remark,
            "notApproved": notApproved
        };

        $.ajax({
            url: this.config.restUrl + "/" + this.config.orgUnitUuid + "/users/" + userUuid + "/reject" + "?managerdelegate=" + (managerdelegate || "false"),
            headers: {
                "content-type": "application/json",
                'X-CSRF-TOKEN': window.token
            },
            method: 'POST',
            data: JSON.stringify(body),
            success: () => {
                $("#approveBtn" + userUuid).hide();
                $("#rejectBtn" + userUuid).hide();
                $("#requestDeleteBtn" + userUuid).hide();
                $("#alreadyDone" + userUuid).show();
                $('#userRemarkModal').modal('toggle');
                $("#remarkRow" + userUuid).show();
                $("#remarkPre" + userUuid).text(remark);

                this.handleBadgeAndPageSwitch(userUuid, number);
            },
            error: (jqXHR) => {
                defaultErrorHandler(jqXHR);
                $("#userRemarkError").show();
            }
        });
    }

    handleRequestDelete = (event) => {
        const button = $(event.currentTarget);
        const userId = button.data("uuid");
        const number = button.data("number");
        const managerdelegate = button.data("managerdelegate");

        swal({
            html: true,
            title: this.config.requestDeleteTitle,
            text: this.config.requestDeleteText,
            showCancelButton: true,
            confirmButtonColor: "#1ab394",
            confirmButtonText: this.config.requestDeleteSave,
            cancelButtonText: this.config.cancelText,
            closeOnConfirm: false,
            closeOnCancel: true
        },
        (confirmed) => {
            if (confirmed === false) {
                // cancelled window
                return;
            }

            $.ajax({
                url: this.config.restUrl + "/" + this.config.orgUnitUuid + "/users/" + userId + "/delete" + "?managerdelegate=" + (managerdelegate || "false"),
                headers: {
                    'X-CSRF-TOKEN': window.token
                },
                method: 'POST',
                success: () => {
                    $("#approveBtn" + userId).hide();
                    $("#rejectBtn" + userId).hide();
                    $("#requestDeleteBtn" + userId).hide();
                    $("#alreadyDone" + userId).show();
                    swal.close();

                    this.handleBadgeAndPageSwitch(userId, number);
                },
                error: defaultErrorHandler
            });
        });
    }

    handleBadgeAndPageSwitch(userId, number) {
        // update badges
        const userCount = $("#badgeUsersText").text();
        const userCountInt = parseInt(userCount, 10);
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
        } else {
            // change to orgUnitPane
            $(".mainTabs").removeClass('active');
            $("#ouTab").addClass('active');
            $("#orgUnitsTab").addClass('active');
        }

        $("html, body").animate({ scrollTop: 0 }, "slow");
    }
}

/**
 * Handles the "org unit" tab: approve / reject flow for the org unit's own
 * (non-user-specific) role assignments.
 */
class OrgUnitService {
    constructor(config) {
        this.config = config;
    }

    init() {
        $(".approveOrgUnitBtn").click(this.handleApprove);
        $(".rejectOrgUnitBtn").click(this.openModal);
        // NOTE: pre-existing bug carried over from the inline script - there is no
        // element with id "modalSaveOrgUnitBtn" in the DOM (the orgUnitRemarkModal
        // fragment's actual button id is "ouModalSaveBtn"). This binding is
        // therefore a no-op. The real binding happens in openModal() below.
        // Flagging rather than silently removing - confirm if this should be cleaned up.
        $("#modalSaveOrgUnitBtn").click(this.handleReject);
        $(".readOrgUnitCheckbox").on('ifChanged', this.handleReadCheckbox);
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

    openModal = (event) => {
        const ouUuid = $(event.currentTarget).data("uuid");

        $("#orgUnitRemarkModalPlaceholder").load(this.config.uiUrl + this.config.attestationUuid + "/orgunit/" + ouUuid + "/ouRemarkFragment", () => {
            $('#orgUnitRemarkModal').modal('toggle');
            window.orgUnitRemarkModalService.init();
            $("#ouModalSaveBtn").click(this.handleReject);
        });
    }

    handleApprove = (event) => {
        const managerdelegate = $(event.currentTarget).data("managerdelegate");

        $.ajax({
            url: this.config.restUrl + "/" + this.config.orgUnitUuid + "/roles/approve" + "?managerdelegate=" + (managerdelegate || "false"),
            method: 'POST',
            headers: {
                'X-CSRF-TOKEN': window.token
            },
            success: () => {
                $("#approveOrgUnitBtn" + this.config.orgUnitUuid).hide();
                $("#rejectOrgUnitBtn" + this.config.orgUnitUuid).hide();
                $("#alreadyDoneOrgUnit" + this.config.orgUnitUuid).show();
                $("#badgeOrgUnits").hide();
            },
            error: defaultErrorHandler
        });
    }

    handleReject = (event) => {
        // Validate that at least one role is selected
        if (!window.orgUnitRemarkModalService.validateSelection()) {
            return;
        }

        let remark = null;
        if (!this.config.hideDescription) {
            remark = $("#orgUnitRemarkField").val();
        }

        const data = {
            remarks: remark,
            notApproved: window.orgUnitRemarkModalService.getNotApprovedRoles()
        };
        const managerdelegate = $(event.currentTarget).data("managerdelegate");

        $.ajax({
            url: this.config.restUrl + "/" + this.config.orgUnitUuid + "/roles/reject" + "?managerdelegate=" + (managerdelegate || "false"),
            headers: {
                "content-type": "application/json",
                'X-CSRF-TOKEN': window.token
            },
            method: 'POST',
            data: JSON.stringify(data),
            success: () => {
                $("#approveOrgUnitBtn" + this.config.orgUnitUuid).hide();
                $("#rejectOrgUnitBtn" + this.config.orgUnitUuid).hide();
                $("#alreadyDoneOrgUnit" + this.config.orgUnitUuid).show();
                $('#orgUnitRemarkModal').modal('toggle');
                $("#remarkRowOrgUnit" + this.config.orgUnitUuid).show();
                $("#remarkPreOrgUnit" + this.config.orgUnitUuid).text(remark);
                $("#badgeOrgUnits").hide();
            },
            error: (jqXHR) => {
                defaultErrorHandler(jqXHR);
                $("#orgUnitRemarkError").show();
            }
        });
    }
}

$(document).ready(function () {
    const config = JSON.parse(document.getElementById('orgUnitsAttestConfig').textContent);
    window.token = $("meta[name='_csrf']").attr("content");

    window.userService = new UserService(config);
    window.userService.init();

    window.orgUnitService = new OrgUnitService(config);
    window.orgUnitService.init();

    window.historyService = new HistoryService();
    window.historyService.init();

    window.utilService = new UtilService();
    window.utilService.init();
});
