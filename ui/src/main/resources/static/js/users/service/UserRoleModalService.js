/**
 * Controls the "assign user role" modal: date pickers, org-unit select,
 * and submitting the new role assignment (directly or via a position).
 */
class UserRoleModalService {
    constructor(config) {
        this.usersUrl = config.usersUrl;
        this.uiUrl = config.uiUrl;
        this.fieldUpdatedMsg = config.fieldUpdatedMsg;
        this.fieldNotUpdatedMsg = config.fieldNotUpdatedMsg;
        this.caseNumberEnabled = config.caseNumberEnabled;
        this.page = config.page;
        this.parentService = null;
    }

    init() {
        const modal = $("#modal-positions");

        $('.assign_role').on('click', (event) => this.assignRoleClicked(event.currentTarget));
        $('.assign_role_directly').on('click', () => {
            window.postponedConstraintsService.validate("assignRoleDirectlyClicked");
        });
        $('#closeModal').on('click', () => this.closeModal());

        modal.on('shown.bs.modal', () => {
            $("#assignDirectlyBtn").focus();
        });
    }

    closeModal() {
        $("#modal-positions").modal('hide');
    }

    setManual(isManual) {
        if (isManual) {
            $("#notifyText").show();
            $("#notifyTitle").show();
        } else {
            $("#notifyText").hide();
            $("#notifyTitle").hide();
        }
    }

    assignRoleDirectlyClicked() {
        const modal = $("#modal-positions");
        const roleId = modal.attr("roleid");
        const userUuid = modal.attr("userUuid");
        const startDate = $('#startDatePicker').data('date');
        const stopDate = $('#stopDatePicker').data('date');
        const dates = `?startDate=${startDate}&stopDate=${stopDate}`;

        const constraints = window.postponedConstraintsService.getConstraintList();

        let caseNumberUrlPart = "";
        if (this.caseNumberEnabled) {
            caseNumberUrlPart = `&casenumber=${$("#addRoleCaseNumber").val()}`;
        }

        $.ajax({
            url: `${this.usersUrl}${userUuid}/addrole/${roleId}${dates}&ouuuid=${$("#addRoleOUSelect").val()}${caseNumberUrlPart}`,
            contentType: 'application/json',
            headers: {
                'X-CSRF-TOKEN': window.token
            },
            type: 'post',
            data: JSON.stringify(constraints),
            success: () => {
                window.modalAjaxService.showInfoNotification(this.fieldUpdatedMsg);
                this.parentService.loadAddUserRoleFragmentWhenReady();
            },
            error: window.errorHandler(this.fieldNotUpdatedMsg)
        });

        modal.modal('hide');
    }

    assignRoleClicked(element) {
        const modal = $("#modal-positions");
        const positionUuid = $(element).val();
        const roleId = modal.attr("roleid");
        const userUuid = modal.attr("userUuid");

        const startDate = $('#startDatePicker').data('date');
        const stopDate = $('#stopDatePicker').data('date');
        const dates = `?startDate=${startDate}&stopDate=${stopDate}`;

        $.ajax({
            url: `${this.usersUrl}position/${positionUuid}/addrole/${roleId}${dates}`,
            contentType: 'application/json',
            headers: {
                'X-CSRF-TOKEN': window.token
            },
            type: 'post',
            success: () => {
                window.modalAjaxService.showInfoNotification(this.fieldUpdatedMsg);
                this.parentService.loadAddUserRoleFragmentWhenReady();
            },
            error: window.errorHandler(this.fieldNotUpdatedMsg)
        });

        modal.modal('hide');
    }

    loadPostponedConstraintsFragment(roleId) {
        $("#postponedConstraintsPlaceholder").empty();
        $("#editPostponedConstraintsPlaceholder").empty();
        $("#bulkAssignPostponedConstraintsPlaceholder").empty();

        $("#postponedConstraintsPlaceholder").load(`${this.uiUrl}postponedconstraints/${roleId}`, () => {
            window.postponedConstraintsService.init(document.getElementById("postponedConstraintsPlaceholder"));

            const postponingAllowed = $("#postponingAllowed").val();
            if (postponingAllowed === "true") {
                $("#positionTable").hide();
            } else {
                $("#positionTable").show();
            }
        });
    }
}
