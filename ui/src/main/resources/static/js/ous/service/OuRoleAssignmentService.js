/**
 * Coordinates the shared state and behavior of the OU roles assignment modal:
 * selection lists, DataTables instances, button visibility, and the OU-exclusion tree.
 */
class OuRoleAssignmentService {
    constructor(config, jsTreeService, notificationService) {
        this.restUrl = config.restUrl;
        this.uiUrl = config.uiUrl;
        this.fieldUpdatedMsg = config.fieldUpdatedMsg;
        this.fieldNotUpdatedMsg = config.fieldNotUpdatedMsg;
        this.noChildrenMsg = config.noChildrenMsg;
        this.titlesEnabled = config.titlesEnabled;

        this.jsTreeService = jsTreeService;
        this.notificationService = notificationService;

        // set externally by the page bootstrap (e.g. manage.js) once the parent roles service exists
        this.parentService = null;
        this.editMode = false;

        this.selectedNegativeTitles = [];
        this.selectedNegativeInheritedTitles = [];
        this.selectedPositiveInheritedTitles = [];
        this.selectedExceptedUsers = [];
        this.selectedTitles = [];
        this.selectedFunctions = [];
        this.selectedExcludedChildOus = [];

        this.titlesTable = null;
        this.negativeTitlesTable = null;
        this.negativeInheritedTable = null;
        this.positiveInheritedTable = null;
        this.exceptedUsersTable = null;
        this.functionTable = null;

        this.ouTreeInitialized = false;
        this.ouTreeLoading = false;
        this.pendingExceptedUuids = [];
    }

    init() {
        $("#assignNegativeWithInheritanceButton").hide();
        $("#assignNegativeWithoutInheritanceButton").hide();
        $("#assignPositiveWithInheritanceButton").hide();

        this.negativeInheritedTable = fragShowDataTableFun('#ou_roles_listTable3', 1, 25, null, [{ orderDataType: "dom-checkbox", className: 'details-control', searchable: false, orderable: true }, {}]);
        this.positiveInheritedTable = fragShowDataTableFun('#ou_roles_listTable5', 1, 25, null, [{ orderDataType: "dom-checkbox", className: 'details-control', searchable: false, orderable: true }, {}]);
        this.titlesTable = fragShowDataTableFun('#ou_roles_listTable1', 1, 25, null, [{ orderDataType: "dom-checkbox", className: 'details-control', searchable: false, orderable: true }, {}]);
        this.negativeTitlesTable = fragShowDataTableFun('#ou_roles_listTable4', 1, 25, null, [{ orderDataType: "dom-checkbox", className: 'details-control', searchable: false, orderable: true }, {}]);
        this.functionTable = fragShowDataTableFun('#ou_roles_functions_table', 1, 25, null, [{ orderDataType: "dom-checkbox", className: 'details-control', searchable: false, orderable: true }, {}]);

        $('a[data-toggle="tab"]').on("shown.bs.tab", (event) => {
            this.handleButtonDisplay();
            if ($(event.target).data('tab-type') === 'ou_roles_inherited_with_exceptions_menu' && !this.ouTreeInitialized) {
                this.initOuTree([]);
            }
        });

        // If the exceptions tab is already active when the modal opens, initOuTree won't be
        // triggered by shown.bs.tab — initialize it here once ouUuid is set.
        $('#modal-ou').on('shown.bs.modal', () => {
            const activeTab = $('#modal-ou .nav-tabs li.active a').data('tab-type');
            if (activeTab === 'ou_roles_inherited_with_exceptions_menu' && !this.ouTreeInitialized) {
                this.initOuTree([]);
            }
        });
    }

    setupCheckboxListeners() {
        this.addSimpleCheckboxListener('.title-checkbox', this.handleTitleCheckbox);
        this.addSimpleCheckboxListener('.positive-titles-inherited-checkbox', this.handlePositiveTitleInheritedCheckbox);
        this.addSimpleCheckboxListener('.inherited-checkbox', this.handleInheritedCheckbox);
        this.addSimpleCheckboxListener('.disinherited-checkbox', this.handleDisinheritedCheckbox);
        this.addSimpleCheckboxListener('.function-checkbox', this.handleFunctionCheckbox);
    }

    initOuTree(preExceptedUuids) {
        if (preExceptedUuids && preExceptedUuids.length > 0) {
            this.pendingExceptedUuids = this.pendingExceptedUuids.concat(preExceptedUuids);
        }

        const treeEl = $('#ou_to_exclude_tree');

        if (this.ouTreeInitialized) {
            if (this.pendingExceptedUuids.length > 0) {
                const instance = this.jsTreeService.getInstance('#ou_to_exclude_tree');
                if (instance) {
                    this.pendingExceptedUuids.forEach((uuid) => instance.select_node(uuid));
                    this.pendingExceptedUuids = [];
                    this.collectExcludedOus();
                }
            }
            return;
        }

        if (this.ouTreeLoading) {
            return;
        }

        const service = this.editMode ? window.ouRolesEditModalService : window.ouRolesModalService;
        const ouUuid = service.ouUuid;

        if (!ouUuid) {
            return;
        }
        this.ouTreeLoading = true;

        $.getJSON(`/rest/ous/${ouUuid}/childtree`, (nodes) => {
            if (!nodes || nodes.length === 0) {
                this.ouTreeLoading = false;
                this.ouTreeInitialized = true;
                treeEl.html(`<p class="text-muted">${this.noChildrenMsg}</p>`);
                $('#assignInheritedWithExceptionsButton').prop('disabled', false);
                return;
            }

            this.jsTreeService.initTree('#ou_to_exclude_tree', {
                core: { data: nodes },
                checkbox: { keep_selected_style: false, three_state: false },
                plugins: ['checkbox']
            });

            treeEl.on('ready.jstree', () => {
                this.ouTreeLoading = false;
                this.ouTreeInitialized = true;
                const instance = this.jsTreeService.getInstance('#ou_to_exclude_tree');
                instance.open_all();
                this.pendingExceptedUuids.forEach((uuid) => instance.select_node(uuid));
                this.pendingExceptedUuids = [];
                this.collectExcludedOus();
                $('#assignInheritedWithExceptionsButton').prop('disabled', false);
            });

            treeEl.on('changed.jstree', () => {
                this.collectExcludedOus();
            });
        }).fail(() => {
            this.ouTreeLoading = false;
        });
    }

    collectExcludedOus() {
        const treeEl = $('#ou_to_exclude_tree');
        if (!this.ouTreeInitialized) {
            return;
        }
        this.selectedExcludedChildOus = treeEl.jstree(true).get_selected();
    }

    addSimpleCheckboxListener(selector, method) {
        $(selector).off("change");
        $(selector).change(method);
    }

    handleDisableButtons(selectorToEnable) {
        $("#titleAssignTitlesButton").prop('disabled', true);
        $("#assignAllButton").prop('disabled', true);
        $("#assignWithExceptionsButton").prop('disabled', true);
        $("#assignWithTitlesAndExceptionsButton").prop('disabled', true);
        $("#assignNegativeWithoutInheritanceButton").prop('disabled', true);
        $("#assignNegativeWithInheritanceButton").prop('disabled', true);
        $("#assignPositiveWithInheritanceButton").prop('disabled', true);
        $("#assignFunctionButton").prop('disabled', true);

        if (selectorToEnable) {
            $(selectorToEnable).prop('disabled', false);
        }
    }

    // Hint: this should be run after selectedTitles and selectedExceptedUsers are fetched from the server
    handleButtonDisplay() {
        const tabType = $('#modal-ou .nav-tabs li.active a[data-toggle="tab"]').data('tab-type');
        const hasTitleSelections = this.selectedTitles.length > 0;
        const hasExceptedUserSelections = this.selectedExceptedUsers.length > 0;

        $("#titleAssignTitlesButton").hide();
        $("#assignWithExceptionsButton").hide();
        $("#assignWithTitlesAndExceptionsButton").hide();
        $("#assignNegativeWithInheritanceButton").hide();
        $("#assignNegativeWithoutInheritanceButton").hide();
        $("#assignPositiveWithInheritanceButton").hide();
        $("#managerInheritButton").hide();
        $("#functionInheritButton").hide();
        $("#assignInheritedWithExceptionsButton").hide();
        $("#titleInheritButton").hide();

        if (tabType === 'ou_roles_title_menu') {
            if (hasTitleSelections && hasExceptedUserSelections) {
                $("#assignWithTitlesAndExceptionsButton").show();
            } else {
                $("#titleAssignTitlesButton").show();
            }
            $("#titleInheritButton").show();
        } else if (tabType === 'ou_roles_excepted_users_menu') {
            if (hasTitleSelections && hasExceptedUserSelections) {
                $("#assignWithTitlesAndExceptionsButton").show();
            } else {
                $("#assignWithExceptionsButton").show();
            }
            $("#titleInheritButton").show();
        } else if (tabType === 'ou_roles_title_inherited_menu') {
            $("#assignNegativeWithInheritanceButton").show();
        } else if (tabType === 'ou_roles_title_disinherited_menu') {
            $("#assignNegativeWithoutInheritanceButton").show();
        } else if (tabType === 'ou_roles_title_positive_inherited_menu') {
            $("#assignPositiveWithInheritanceButton").show();
        } else if (tabType === 'ou_roles_manager_menu') {
            $("#managerInheritButton").show();
        } else if (tabType === 'ou_roles_functions_menu') {
            $("#functionInheritButton").show();
        } else if (tabType === 'ou_roles_inherited_with_exceptions_menu') {
            $('#assignInheritedWithExceptionsButton').show();
        }
    }

    handleCheckboxGeneric(dataId, checked, list, btnToEnable) {
        if (checked) {
            list.push(dataId);
        } else {
            const index = list.indexOf(dataId);
            if (index !== -1) {
                list.splice(index, 1);
            }
        }

        this.handleButtonDisplay();

        const hasTitleSelections = this.selectedTitles.length > 0;
        const hasExceptedUserSelections = this.selectedExceptedUsers.length > 0;

        let toEnable;
        if (hasTitleSelections && hasExceptedUserSelections) {
            toEnable = "#assignWithTitlesAndExceptionsButton";
        } else if (list.length > 0) {
            toEnable = btnToEnable;
        } else {
            toEnable = "#assignAllButton";
        }

        this.handleDisableButtons(toEnable);
    }

    // NOTE: the following six methods are bound directly as jQuery change handlers
    // (not called as instance methods), so `this` refers to the checkbox element,
    // matching the checked/data-id read below. They reach the singleton instance
    // via window.ouRoleAssignmentService instead of `this`.
    handleTitleCheckbox() {
        window.ouRoleAssignmentService.handleCheckboxGeneric($(this).data('id'), this.checked, window.ouRoleAssignmentService.selectedTitles, "#titleAssignTitlesButton");
    }

    handleExceptedUsersCheckbox() {
        window.ouRoleAssignmentService.handleCheckboxGeneric($(this).data('id'), this.checked, window.ouRoleAssignmentService.selectedExceptedUsers, "#assignWithExceptionsButton");
    }

    handleInheritedCheckbox() {
        window.ouRoleAssignmentService.handleCheckboxGeneric($(this).data('id'), this.checked, window.ouRoleAssignmentService.selectedNegativeInheritedTitles, "#assignNegativeWithInheritanceButton");
    }

    handleDisinheritedCheckbox() {
        window.ouRoleAssignmentService.handleCheckboxGeneric($(this).data('id'), this.checked, window.ouRoleAssignmentService.selectedNegativeTitles, "#assignNegativeWithoutInheritanceButton");
    }

    handlePositiveTitleInheritedCheckbox() {
        window.ouRoleAssignmentService.handleCheckboxGeneric($(this).data('id'), this.checked, window.ouRoleAssignmentService.selectedPositiveInheritedTitles, "#assignPositiveWithInheritanceButton");
    }

    handleFunctionCheckbox() {
        window.ouRoleAssignmentService.handleCheckboxGeneric($(this).data('id'), this.checked, window.ouRoleAssignmentService.selectedFunctions, "#assignFunctionButton");
    }

    closeModal() {
        $('#modal-ou').modal('hide');
        this.resetUI();
    }

    resetUI() {
        this.clearSelections();
        this.ouTreeInitialized = false;
        this.ouTreeLoading = false;
        this.pendingExceptedUuids = [];
        if ($('#ou_to_exclude_tree').jstree(true)) {
            $('#ou_to_exclude_tree').jstree('destroy');
        }
        $('.nav-tabs a[href="#ou_roles_title_menu"]').tab('show');
    }

    handleExceptedRoleAssignment() {
        const service = this.editMode ? window.ouRolesEditModalService : window.ouRolesModalService;
        const id = this.editMode ? service.assignmentId : service.roleId;
        service.assignRole(service.roleType, false, id, [], this.selectedExceptedUsers, []);
    }

    handleInheritedWithExceptionsAssignment() {
        const service = this.editMode ? window.ouRolesEditModalService : window.ouRolesModalService;
        const caseNumber = $('#caseNumber').val();
        const startDate = $('#startDatePickerOU').data('date');
        const stopDate = $('#stopDatePickerOU').data('date');
        const ouUuid = service.ouUuid;
        const objType = service.roleType;
        const action = this.editMode ? 'edit' : 'add';
        const id = this.editMode ? service.assignmentId : service.roleId;
        const endpoint = `${this.restUrl}${action}${objType}/${ouUuid}/${id}/inheritWithExceptedOus?startDate=${startDate}&stopDate=${stopDate}&caseNumber=${caseNumber}`;

        this.showLoadingState();
        $.ajax({
            url: endpoint,
            method: "POST",
            headers: { 'X-CSRF-TOKEN': window.token },
            contentType: 'application/json',
            data: JSON.stringify({ excludedChildOus: this.selectedExcludedChildOus }),
            error: (response) => {
                this.hideLoadingState();
                errorHandler(this.fieldNotUpdatedMsg)(response);
                this.closeModal();
            },
            success: () => {
                this.hideLoadingState();
                this.notificationService.showInfoNotification(this.fieldUpdatedMsg);
                this.closeModal();
                if (this.editMode) {
                    this.parentService.loadRolesFragment();
                } else if (objType === "role") {
                    this.parentService.loadAddUserRoleFragment();
                } else if (objType === "rolegroup") {
                    this.parentService.loadAddRoleGroupFragment();
                }
            }
        });
    }

    handleTitleRoleAssignment() {
        const service = this.editMode ? window.ouRolesEditModalService : window.ouRolesModalService;
        const id = this.editMode ? service.assignmentId : service.roleId;
        service.assignRole(service.roleType, false, id, this.selectedTitles, [], []);
    }

    handleTitleAndExceptedRoleAssignment() {
        const service = this.editMode ? window.ouRolesEditModalService : window.ouRolesModalService;
        const id = this.editMode ? service.assignmentId : service.roleId;
        service.assignRole(service.roleType, false, id, this.selectedTitles, this.selectedExceptedUsers, []);
    }

    handleEveryoneRoleAssignment(inherit) {
        const service = this.editMode ? window.ouRolesEditModalService : window.ouRolesModalService;
        const id = this.editMode ? service.assignmentId : service.roleId;
        service.assignRole(service.roleType, inherit, id, [], [], []);
    }

    handleNegativeAssignment(inherit) {
        const titleUuids = inherit ? this.selectedNegativeInheritedTitles : this.selectedNegativeTitles;
        const service = this.editMode ? window.ouRolesEditModalService : window.ouRolesModalService;
        const id = this.editMode ? service.assignmentId : service.roleId;
        service.assignRole(service.roleType, inherit, id, titleUuids, [], [], true);
    }

    handlePositiveInheritedTitleAssignment() {
        const service = this.editMode ? window.ouRolesEditModalService : window.ouRolesModalService;
        const id = this.editMode ? service.assignmentId : service.roleId;
        service.assignRole(service.roleType, true, id, this.selectedPositiveInheritedTitles, [], [], false);
    }

    handleManagerRoleAssignment(includeSubstitutes, inherit) {
        const service = this.editMode ? window.ouRolesEditModalService : window.ouRolesModalService;
        const id = this.editMode ? service.assignmentId : service.roleId;
        service.assignRole(service.roleType, inherit, id, [], [], [], false, true, includeSubstitutes);
    }

    handleFunctionRoleAssignment(inherit) {
        const service = this.editMode ? window.ouRolesEditModalService : window.ouRolesModalService;
        const id = this.editMode ? service.assignmentId : service.roleId;
        service.assignRole(service.roleType, inherit, id, [], [], this.selectedFunctions, false);
    }

    clearLists() {
        this.selectedNegativeTitles = [];
        this.selectedNegativeInheritedTitles = [];
        this.selectedPositiveInheritedTitles = [];
        this.selectedExceptedUsers = [];
        this.selectedTitles = [];
        this.selectedFunctions = [];
        this.selectedExcludedChildOus = [];
    }

    clearSelections() {
        if (this.titlesEnabled) {
            this.clearSelection('#modal-ou .title-checkbox:checked');
            this.clearDatatableSelection('.inherited-checkbox', this.negativeInheritedTable);
            this.clearSelection('#modal-ou .disinherited-checkbox:checked');
            this.clearDatatableSelection('.positive-titles-inherited-checkbox', this.positiveInheritedTable);
        }
        this.clearDatatableSelection('.exceptedusers-checkbox', this.exceptedUsersTable);
        this.clearDatatableSelection('.function-checkbox', this.functionTable);
    }

    clearSelection(selector) {
        $(selector).prop('checked', false);
    }

    clearDatatableSelection(selector, table) {
        if (table == null) {
            return;
        }

        table.rows().every(function () {
            const row = $(this.node());
            row.find(selector).prop('checked', false);
        });

        $(selector).prop('checked', false);
    }

    showLoadingState() {
        $('#modal-ou .modal-footer button').prop('disabled', true);
        $('#assignmentLoadingOverlay').css('display', 'flex');
    }

    hideLoadingState() {
        $('#modal-ou .modal-footer button').prop('disabled', false);
        $('#assignmentLoadingOverlay').hide();
        this.handleButtonDisplay();
    }
}
