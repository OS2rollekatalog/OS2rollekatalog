/**
 * Controls the "assign role group" modal: date pickers, org-unit select,
 * and submitting the new role-group assignment (directly or via a position).
 */
class RoleGroupModalService {
    constructor(config) {
        this.usersUrl = config.usersUrl;
        this.fieldUpdatedMsg = config.fieldUpdatedMsg;
        this.fieldNotUpdatedMsg = config.fieldNotUpdatedMsg;
        this.caseNumberEnabled = config.caseNumberEnabled;
        this.page = config.page;
        this.parentService = null;
    }

    init() {
        const modalGroups = $("#modal-groups");

        $('.assign_group').on('click', (event) => this.assignGroupClicked(event.currentTarget));
        $('.assign_group_directly').on('click', () => this.assignGroupDirectlyClicked());
        $('#closeRoleGroupModal').on('click', () => this.closeModal());

        modalGroups.on('shown.bs.modal', () => {
            $("#assignDirectlyRGBtn").focus();
        });
    }

    closeModal() {
        $("#modal-groups").modal('hide');
    }

    assignGroupDirectlyClicked() {
        const modalGroups = $("#modal-groups");
        const roleId = modalGroups.attr("rolegroupid");
        const userUuid = modalGroups.attr("userUuid");

        const startDate = $('#groupStartDatePicker').data('date');
        const stopDate = $('#groupStopDatePicker').data('date');
        const dates = `?startDate=${startDate}&stopDate=${stopDate}`;

        let caseNumberUrlPart = "";
        if (this.caseNumberEnabled) {
            caseNumberUrlPart = `&casenumber=${$("#addRoleGroupCaseNumber").val()}`;
        }

        const url = `${this.usersUrl}${userUuid}/addgroup/${roleId}${dates}&ouuuid=${$("#addRoleOUSelect").val()}${caseNumberUrlPart}`;

        $.ajax(window.modalAjaxService.getAjaxObject(url, this.fieldUpdatedMsg, this.fieldNotUpdatedMsg))
            .done(() => this.parentService.loadAddRoleGroupFragmentWhenReady());

        modalGroups.modal('hide');
    }

	assignGroupClicked(element) {
        const modalGroups = $("#modal-groups");
        const positionUuid = $(element).val();
        const roleId = modalGroups.attr("rolegroupid");
        const userUuid = modalGroups.attr("userUuid");

        const startDate = $('#groupStartDatePicker').data('date');
        const stopDate = $('#groupStopDatePicker').data('date');
        const dates = `?startDate=${startDate}&stopDate=${stopDate}`;

        const url = `${this.usersUrl}position/${positionUuid}/addgroup/${roleId}${dates}`;

        $.ajax(window.modalAjaxService.getAjaxObject(url, this.fieldUpdatedMsg, this.fieldNotUpdatedMsg))
            .done(() => this.parentService.loadAddRoleGroupFragmentWhenReady());

        modalGroups.modal('hide');
    }
}
