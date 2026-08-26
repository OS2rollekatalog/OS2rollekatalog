/**
 * Handles the "assign role" modal when editing an existing role assignment on an OU
 */
class OuRolesEditModalService {
    constructor(config, ouRoleAssignmentService) {
        this.config = config;
        this.ouRoleAssignmentService = ouRoleAssignmentService;
        this.header = config.headerChooseEdit;
        this.ouUuid = null;
        this.roleType = null;
        this.assignmentId = null;
        this.inherit = null;
    }

    fetchCheckedTitles() {
        return $.ajax({
            url: `${this.ouRoleAssignmentService.restUrl}${this.ouUuid}/${this.roleType}/${this.assignmentId}/titles`,
            method: "GET",
            error: errorHandler(this.ouRoleAssignmentService.fieldNotUpdatedMsg),
            success: (response) => {
                this.ouRoleAssignmentService.selectedTitles = response;
                this.updateDatatableWithCheckboxChecks('.title-checkbox', this.ouRoleAssignmentService.titlesTable, this.ouRoleAssignmentService.selectedTitles);
            }
        });
    }

    fetchCheckedNegativeTitles() {
        return $.ajax({
            url: `${this.ouRoleAssignmentService.restUrl}${this.ouUuid}/${this.roleType}/${this.assignmentId}/negativetitles`,
            method: "GET",
            error: errorHandler(this.ouRoleAssignmentService.fieldNotUpdatedMsg),
            success: (response) => {
                this.ouRoleAssignmentService.selectedNegativeTitles = response;
                this.updateDatatableWithCheckboxChecks('.disinherited-checkbox', this.ouRoleAssignmentService.negativeTitlesTable, this.ouRoleAssignmentService.selectedNegativeTitles);
            }
        });
    }

    fetchCheckedNegativeInheritedTitles() {
        return $.ajax({
            url: `${this.ouRoleAssignmentService.restUrl}${this.ouUuid}/${this.roleType}/${this.assignmentId}/inheritedtitles?positive=false`,
            method: "GET",
            error: errorHandler(this.ouRoleAssignmentService.fieldNotUpdatedMsg),
            success: (response) => {
                this.ouRoleAssignmentService.selectedNegativeInheritedTitles = response;
                this.updateDatatableWithCheckboxChecks('.inherited-checkbox', this.ouRoleAssignmentService.negativeInheritedTable, this.ouRoleAssignmentService.selectedNegativeInheritedTitles);
            }
        });
    }

    fetchCheckedPositiveInheritedTitles() {
        return $.ajax({
            url: `${this.ouRoleAssignmentService.restUrl}${this.ouUuid}/${this.roleType}/${this.assignmentId}/inheritedtitles?positive=true`,
            method: "GET",
            error: errorHandler(this.ouRoleAssignmentService.fieldNotUpdatedMsg),
            success: (response) => {
                this.ouRoleAssignmentService.selectedPositiveInheritedTitles = response;
                this.updateDatatableWithCheckboxChecks('.positive-titles-inherited-checkbox', this.ouRoleAssignmentService.positiveInheritedTable, this.ouRoleAssignmentService.selectedPositiveInheritedTitles);
            }
        });
    }

    fetchCheckedFunctions() {
        return $.ajax({
            url: `${this.ouRoleAssignmentService.restUrl}${this.ouUuid}/${this.roleType}/${this.assignmentId}/functions`,
            method: "GET",
            error: errorHandler(this.ouRoleAssignmentService.fieldNotUpdatedMsg),
            success: (response) => {
                this.ouRoleAssignmentService.selectedFunctions = response;
                this.updateDatatableWithCheckboxChecks('.function-checkbox', this.ouRoleAssignmentService.functionTable, this.ouRoleAssignmentService.selectedFunctions);
            }
        });
    }

    fetchCheckedExceptedOus() {
        return $.ajax({
            url: `${this.ouRoleAssignmentService.restUrl}${this.ouUuid}/${this.roleType}/${this.assignmentId}/exceptedous`,
            method: "GET",
            error: errorHandler(this.ouRoleAssignmentService.fieldNotUpdatedMsg),
            success: (response) => {
                this.ouRoleAssignmentService.selectedExcludedChildOus = response;
                this.ouRoleAssignmentService.initOuTree(response);
            }
        });
    }

    updateDatatableWithCheckboxChecks(checkboxClass, table, selectedIds) {
        table.off('draw.dt.checkboxUpdate');

        table.on('draw.dt.checkboxUpdate', () => {
            $(checkboxClass).each(function () {
                const dataId = $(this).data('id');
                if (selectedIds.includes(dataId)) {
                    $(this).prop('checked', true);
                }
            });
        });

        const currentPage = table.page();
        const totalPages = table.page.info().pages;

        for (let i = 0; i < totalPages; i++) {
            table.page(i).draw(false);
        }

        table.page(currentPage).draw(false);
    }

    handleDates(startDate, stopDate) {
        if (startDate) {
            $('#startDatePickerOU').data("DateTimePicker").date(startDate);
        } else {
            $('#startDatePickerOU').data("DateTimePicker").clear();
            $('#startDatePickerOU').data("DateTimePicker").date(new Date());
        }

        if (stopDate) {
            $('#stopDatePickerOU').data("DateTimePicker").date(stopDate);
        } else {
            $('#stopDatePickerOU').data("DateTimePicker").clear();
        }
    }

    showAssignModal(titlesEnabled, startDate, stopDate, assignmentType, assignmentId, roleName) {
    	const assignmentTypeNum = Number(assignmentType);
        this.handleDates(startDate, stopDate);

        $('#modal-ou #dataTabs').show();
        $('#modal-ou .tab-content').show();
        this.ouRoleAssignmentService.editMode = true;
        this.ouRoleAssignmentService.clearLists();
        this.ouRoleAssignmentService.clearSelections();
        this.ouRoleAssignmentService.handleDisableButtons();
        this.ouRoleAssignmentService.handleDisableButtons('#assignAllButton');

        const url = `${this.ouRoleAssignmentService.uiUrl}${this.roleType}/exceptedusers2/${this.ouUuid}/${assignmentId}`;
        $("#ou_roles_excepted_users_menu").load(url, () => {
            this.ouRoleAssignmentService.exceptedUsersTable = fragShowDataTableFun('#ou_roles_listTable2', 1, 25, null, [{ orderDataType: "dom-checkbox", className: 'details-control', searchable: false, orderable: true }, {}, {}]);

            const allRows = $('#ou_roles_listTable2').DataTable().rows().nodes();
            const selectedExceptedUsers = this.ouRoleAssignmentService.selectedExceptedUsers;
            $(allRows).each(function () {
                const checkbox = $(this).find('input[type="checkbox"]');
                if (checkbox.length > 0 && checkbox.is(':checked')) {
                    selectedExceptedUsers.push(checkbox.data('id'));
                }
            });

            this.ouRoleAssignmentService.addSimpleCheckboxListener('.exceptedusers-checkbox', this.ouRoleAssignmentService.handleExceptedUsersCheckbox);
            $('#ou_roles_listTable2').on('draw.dt', () => {
                this.ouRoleAssignmentService.addSimpleCheckboxListener('.exceptedusers-checkbox', this.ouRoleAssignmentService.handleExceptedUsersCheckbox);
            });

            if (assignmentTypeNum === 0) {
                $('#modal-ou a[data-toggle="tab"][href="#ou_roles_excepted_users_menu"]').tab('show');
                this.ouRoleAssignmentService.handleDisableButtons('#assignWithExceptionsButton');
            } else if (titlesEnabled) {
                if (assignmentTypeNum >= 1) {
                    this.fetchCheckedTitles().done(() => {
                        $('#modal-ou a[data-toggle="tab"][href="#ou_roles_title_menu"]').tab('show');
                        this.ouRoleAssignmentService.handleButtonDisplay();
                        this.ouRoleAssignmentService.handleDisableButtons('#titleAssignTitlesButton');
                    });
                } else if (assignmentTypeNum === -3) {
                    this.fetchCheckedNegativeInheritedTitles().done(() => {
                        $('#modal-ou a[data-toggle="tab"][href="#ou_roles_title_inherited_menu"]').tab('show');
                        this.ouRoleAssignmentService.handleButtonDisplay();
                        this.ouRoleAssignmentService.handleDisableButtons('#assignNegativeWithInheritanceButton');
                    });
                } else if (assignmentTypeNum === -4) {
                    this.fetchCheckedNegativeTitles().done(() => {
                        $('#modal-ou a[data-toggle="tab"][href="#ou_roles_title_disinherited_menu"]').tab('show');
                        this.ouRoleAssignmentService.handleButtonDisplay();
                        this.ouRoleAssignmentService.handleDisableButtons('#assignNegativeWithoutInheritanceButton');
                    });
                } else if (assignmentTypeNum === -5) {
                    this.fetchCheckedPositiveInheritedTitles().done(() => {
                        $('#modal-ou a[data-toggle="tab"][href="#ou_roles_title_positive_inherited_menu"]').tab('show');
                        this.ouRoleAssignmentService.handleButtonDisplay();
                        this.ouRoleAssignmentService.handleDisableButtons('#assignPositiveWithInheritanceButton');
                    });
                } else if (assignmentTypeNum === -6) {
                    this.fetchCheckedTitles().done(() => {
                        $('#modal-ou a[data-toggle="tab"][href="#ou_roles_title_menu"]').tab('show');
                        this.ouRoleAssignmentService.handleButtonDisplay();
                        this.ouRoleAssignmentService.handleDisableButtons('#assignWithTitlesAndExceptionsButton');
                    });
                } else if (assignmentTypeNum === -7 || assignmentTypeNum === -8 || assignmentTypeNum === -9 || assignmentTypeNum === -10) {
                    $('#modal-ou a[data-toggle="tab"][href="#ou_roles_manager_menu"]').tab('show');
                } else if (assignmentTypeNum === -11 || assignmentTypeNum === -12) {
                    this.fetchCheckedFunctions().done(() => {
                        $('#modal-ou a[data-toggle="tab"][href="#ou_roles_functions_menu"]').tab('show');
                        this.ouRoleAssignmentService.handleButtonDisplay();
                        this.ouRoleAssignmentService.handleDisableButtons('#assignFunctionButton');
                    });
                } else if (assignmentTypeNum === -13) {
                    this.fetchCheckedExceptedOus().done(() => {
                        $('#modal-ou a[data-toggle="tab"][href="#ou_roles_inherited_with_exceptions_menu"]').tab('show');
                        this.ouRoleAssignmentService.handleButtonDisplay();
                        this.ouRoleAssignmentService.handleDisableButtons('#assignInheritedWithExceptionsButton');
                    });
                }
            }
        });

        this.ouRoleAssignmentService.handleButtonDisplay();

        $('#modal-ou .table:not(#ou_roles_listTable2)').off();
        this.ouRoleAssignmentService.setupCheckboxListeners();
        $('#modal-ou .table:not(#ou_roles_listTable2)').on('draw.dt', () => {
            this.ouRoleAssignmentService.setupCheckboxListeners();
        });

        $("#modal-ou").attr('assignmentId', assignmentId);
        $("#ouRolesModalHeader").text(this.header);
        $("#ouRolesModalRoleName").text(roleName);

        $("#modal-ou").modal({
            backdrop: 'static',
            keyboard: false
        });
    }

    assignRole(objType, inherit, id, titleUuids, exceptedUserUuids, functionUuids, negativeAssignment = false, manager = false, substitutes = false) {
        const startDate = $('#startDatePickerOU').data('date');
        const stopDate = $('#stopDatePickerOU').data('date');

        let endpoint = `${this.ouRoleAssignmentService.restUrl}edit${objType}/${this.ouUuid}/${id}`;
        endpoint += `?startDate=${startDate}&stopDate=${stopDate}`;

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
                setTimeout(() => { this.ouRoleAssignmentService.parentService.loadRolesFragment(); }, 200);
                this.ouRoleAssignmentService.closeModal();
            },
            success: () => {
                this.ouRoleAssignmentService.hideLoadingState();
                this.ouRoleAssignmentService.notificationService.showInfoNotification(this.ouRoleAssignmentService.fieldUpdatedMsg);
                setTimeout(() => { this.ouRoleAssignmentService.parentService.loadRolesFragment(); }, 200);
                this.ouRoleAssignmentService.closeModal();
            }
        });
    }
}
