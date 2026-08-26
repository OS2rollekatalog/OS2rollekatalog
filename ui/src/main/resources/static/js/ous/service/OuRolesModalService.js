/**
 * Handles the "assign role" modal when creating a new (non-edit) role assignment on an OU
 */
class OuRolesModalService {
    constructor(config, ouRoleAssignmentService) {
        this.config = config;
        this.ouRoleAssignmentService = ouRoleAssignmentService;
        this.header = config.headerChoose;
        this.ouUuid = null;
        this.roleType = null;
        this.roleId = null;
    }

    showAssignModal(titlesEnabled, startDate, stopDate, roleName) {
        $('#stopDatePickerOU').data("DateTimePicker").clear();
        $('#startDatePickerOU').data("DateTimePicker").clear();
        $('#startDatePickerOU').data("DateTimePicker").date(new Date());

        $('#modal-ou #dataTabs').show();
        $('#modal-ou .tab-content').show();
        this.ouRoleAssignmentService.editMode = false;
        this.ouRoleAssignmentService.clearLists();
        this.ouRoleAssignmentService.clearSelections();

        this.ouRoleAssignmentService.handleDisableButtons('#assignAllButton');

        const url = `${this.ouRoleAssignmentService.uiUrl}users/${this.ouUuid}`;
        $("#ou_roles_excepted_users_menu").load(url, () => {
            this.ouRoleAssignmentService.exceptedUsersTable = fragShowDataTableFun('#ou_roles_listTable2', 1, 25, null, [{ orderDataType: "dom-checkbox", className: 'details-control', searchable: false, orderable: true }, {}, {}]);

            this.ouRoleAssignmentService.addSimpleCheckboxListener('.exceptedusers-checkbox', this.ouRoleAssignmentService.handleExceptedUsersCheckbox);
            $('#ou_roles_listTable2').on('draw.dt', () => {
                this.ouRoleAssignmentService.addSimpleCheckboxListener('.exceptedusers-checkbox', this.ouRoleAssignmentService.handleExceptedUsersCheckbox);
            });
        });

        if (titlesEnabled) {
            this.ouRoleAssignmentService.clearSelection('#modal-ou .title-checkbox:checked');
            $('a[data-toggle="tab"][href="#ou_roles_title_menu"]').tab('show');
        } else {
            this.ouRoleAssignmentService.clearDatatableSelection('.exceptedusers-checkbox', this.ouRoleAssignmentService.exceptedUsersTable);
            $('a[data-toggle="tab"][href="#ou_roles_excepted_users_menu"]').tab('show');
        }

        this.ouRoleAssignmentService.handleButtonDisplay();

        $('#modal-ou .table:not(#ou_roles_listTable2)').off();
        this.ouRoleAssignmentService.setupCheckboxListeners();
        $('#modal-ou .table:not(#ou_roles_listTable2)').on('draw.dt', () => {
            this.ouRoleAssignmentService.setupCheckboxListeners();
        });

        $("#ouRolesModalHeader").text(this.header);
        $("#ouRolesModalRoleName").text(roleName);

        $("#modal-ou").modal({
            backdrop: 'static',
            keyboard: false
        });
    }

    assignRole(objType, inherit, id, titleUuids, exceptedUserUuids, functionUuids, negativeAssignment = false, manager = false, substitutes = false) {
        const caseNumber = $('#caseNumber').val();
        const startDate = $('#startDatePickerOU').data('date');
        const stopDate = $('#stopDatePickerOU').data('date');

        let endpoint = `${this.ouRoleAssignmentService.restUrl}add${objType}/${this.ouUuid}/${id}`;
        endpoint += `?startDate=${startDate}&stopDate=${stopDate}`;
        endpoint += `&caseNumber=${caseNumber}`;

        if (inherit) {
            endpoint += "&inherit=true";
        }
        if (negativeAssignment) {
            endpoint += "&negativeAssignment=true";
        }
        if (manager) {
            endpoint += "&manager=true";
            if (substitutes) {
                endpoint += "&substitutes=true";
            }
        }

        this.ouRoleAssignmentService.showLoadingState();

        $.ajax({
            url: endpoint,
            method: "POST",
            headers: { 'X-CSRF-TOKEN': window.token },
            contentType: 'application/json',
            data: JSON.stringify({ titleUuids, exceptedUserUuids, functionUuids }),
            error: (response) => {
                this.ouRoleAssignmentService.hideLoadingState();
                errorHandler(this.ouRoleAssignmentService.fieldNotUpdatedMsg)(response);
                this.ouRoleAssignmentService.closeModal();
            },
            success: (response) => {
                this.ouRoleAssignmentService.hideLoadingState();

                if (response.success && response.users > 0) {
                    const optionalSuccessMessage = this.config.usersAlreadyAssignedDirectlyMsg.format(response.users);
                    this.ouRoleAssignmentService.notificationService.showWarnNotification(optionalSuccessMessage);
                } else {
                    // TODO: should we inform the user that we did not make any change?
                    this.ouRoleAssignmentService.notificationService.showInfoNotification(this.ouRoleAssignmentService.fieldUpdatedMsg);
                }

                this.ouRoleAssignmentService.closeModal();
                if (objType === "role") {
                    this.ouRoleAssignmentService.parentService.loadAddUserRoleFragment();
                } else if (objType === "rolegroup") {
                    this.ouRoleAssignmentService.parentService.loadAddRoleGroupFragment();
                }
            }
        });
    }
}
