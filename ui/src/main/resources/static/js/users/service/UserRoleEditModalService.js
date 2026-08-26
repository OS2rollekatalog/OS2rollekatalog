/**
 * Controls the "edit role assignment" modal: date pickers, org-unit select,
 * and submitting the updated assignment.
 */
class UserRoleEditModalService {
    constructor(config) {
        this.usersUrl = config.usersUrl;
        this.uiUrl = config.uiUrl;
        this.missingPostponedConstraintService = config.missingPostponedConstraintService;
        this.caseNumberEnabled = config.caseNumberEnabled;
        this.fieldUpdatedMsg = config.fieldUpdatedMsg;
        this.fieldNotUpdatedMsg = config.fieldNotUpdatedMsg;
        this.parentService = null;
    }

    modal() {
        return $("#modal-edit");
    }

    init() {
        $('#closeEditModal').on('click', () => this.closeModal());

        if (!this.missingPostponedConstraintService) {
            $('#updateBtn').on("click", () => {
                window.postponedConstraintsService.validate("edit");
            });
        } else {
            $('#updateBtn').on("click", () => {
                this.updateClicked();
            });
        }

        this.modal().on('shown.bs.modal', () => {
            $("#updateBtn").focus();
        });
    }

    showModal(user, startDate, stopDate, type, assignmentId, assignedThrough, selectedOrgUnit, possibleOrgUnits, caseNumber, roleName) {
        const editModal = this.modal();
        editModal.modal({
            backdrop: 'static',
            keyboard: false
        });

        $('#startDatePickerEdit').data("DateTimePicker").clear();
        if (startDate) {
            $('#startDatePickerEdit').data("DateTimePicker").date(startDate);
        } else {
            $('#startDatePickerEdit').data("DateTimePicker").date(new Date());
        }

        $('#stopDatePickerEdit').data("DateTimePicker").clear();
        if (stopDate) {
            $('#stopDatePickerEdit').data("DateTimePicker").date(stopDate);
        }

        $("#editAssignmentRoleName").text(roleName);

        $("#editRoleOUSelect").empty();
        for (let i = 0; i < possibleOrgUnits.length; i++) {
            const current = possibleOrgUnits[i];
            if (current.uuid === selectedOrgUnit) {
                $("#editRoleOUSelect").append(`<option selected="selected" value="${current.uuid}">${current.name}</option>`);
            } else {
                $("#editRoleOUSelect").append(`<option value="${current.uuid}">${current.name}</option>`);
            }
        }

        if (this.caseNumberEnabled) {
            $("#editRoleCaseNumber").val(caseNumber);
        }

        editModal.attr("type", type);
        editModal.attr("assignmentId", assignmentId);
        editModal.attr("assignedThrough", assignedThrough);
        editModal.attr("user", user);
    }

    updateClicked() {
        const editModal = this.modal();
        const assignmentId = editModal.attr("assignmentid");
        const userUuid = editModal.attr("user");
        const assignedThrough = editModal.attr("assignedthrough");
        const type = editModal.attr("type");
        const selectedOU = $("#editRoleOUSelect").val();

        const startDate = $('#startDatePickerEdit').data('date');
        const stopDate = $('#stopDatePickerEdit').data('date');
        const dates = `?startDate=${startDate}&stopDate=${stopDate}`;

        let caseNumberUrlPart = "";
        if (this.caseNumberEnabled) {
            caseNumberUrlPart = `&casenumber=${$("#editRoleCaseNumber").val()}`;
        }

        const updateRestEndpoint = `${this.usersUrl}${userUuid}/editassignment/${type}/${assignedThrough}/${assignmentId}${dates}&ouuuid=${selectedOU}${caseNumberUrlPart}`;
        let constraints = [];
        if (!this.missingPostponedConstraintService) {
            constraints = window.postponedConstraintsService.getConstraintList();
        }

        $.ajax({
			url: updateRestEndpoint,
			contentType: 'application/json',
			headers: {
				'X-CSRF-TOKEN': window.token
			},
			type: 'post',
			data: JSON.stringify(constraints),
			success: () => {
				window.modalAjaxService.showInfoNotification(this.fieldUpdatedMsg);
				this.parentService.loadRolesFragmentWhenReady();
			},
			error: window.errorHandler(this.fieldNotUpdatedMsg)
		});

        editModal.modal('hide');
    }

    closeModal() {
        this.modal().modal('hide');
    }

    loadPostponedConstraintsFragment(assignmentId, type, user) {
        if (type === "USERROLE") {
            $("#postponedConstraintsPlaceholder").empty();
            $("#editPostponedConstraintsPlaceholder").empty();
            $("#bulkAssignPostponedConstraintsPlaceholder").empty();

            $("#editPostponedConstraintsPlaceholder").load(`${this.uiUrl}${user}/postponedconstraints/edit/${assignmentId}`, () => {
                window.postponedConstraintsService.init(document.getElementById("editPostponedConstraintsPlaceholder"));
            });
        }
    }
}
