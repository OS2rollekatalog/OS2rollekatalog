/**
 * Handles role assignments on the user "manage" page: loading the roles
 * tab, adding/removing/editing assignments, and the inactive-roles filter.
 */
class RolesService {
    constructor(config) {
        this.UIUrl = config.UIUrl;
        this.restUrl = config.restUrl;
        this.user = config.user;
        this.deleteRoleAssignmentTitleTxt = config.deleteRoleAssignmentTitleTxt;
        this.deleteRoleAssignmentBodyTxt = config.deleteRoleAssignmentBodyTxt;
        this.deleteRoleAssignmentErrorMessage = config.deleteRoleAssignmentErrorMessage;
        this.deleteAssignmentConfirmTxt = config.deleteAssignmentConfirmTxt;
        this.deleteAssignmentCancelTxt = config.deleteAssignmentCancelTxt;
        this.possibleOrgUnits = config.possibleOrgUnits;
        this.reqRedirectUri = config.reqRedirectUri;
        this.txtQueueSpinner = config.txtQueueSpinner;
        this.bulkRemoveTitleTxt = config.bulkRemoveTitleTxt;
        this.bulkRemoveBodyTxt = config.bulkRemoveBodyTxt;
        this.bulkRemoveErrorTxt = config.bulkRemoveErrorTxt;
        this.selectedCountLabel = config.selectedCountLabel;
        this.bulkRemoveModeActive = false;
    }

    loadRolesFragment() {
        $("#roles_menu").load(this.UIUrl + this.user + "/roles", () => {
            const hideColumn = [2, 5, 6];
            window.currentTable = showDataTableWithHiddenColumns('#listTable', 0, hideColumn, 100, "user_manage_list_of_roles");
            dataTablesRefreshIcons($('#dataTableDropdown'));

            let showInactive = window.showInactiveState !== undefined ? window.showInactiveState : true;

            this.updateButtonAppearance(showInactive);
            this.filterInactiveRoles(showInactive);

            $('#inactiveRolesToggle').on('click', () => {
                showInactive = !showInactive;
                window.showInactiveState = showInactive;
                this.updateButtonAppearance(showInactive);
                this.filterInactiveRoles(showInactive);
            });
        });
    }

    updateButtonAppearance(isActive) {
        const button = $('#inactiveRolesToggle');
        const checkbox = $('#inactiveRolesCheckbox');
        const icon = button.find('.fa');
        const text = $('#toggleText');

        checkbox.prop('checked', isActive);

        if (isActive) {
            $('#inactiveRolesToggle').addClass('active');
            icon.removeClass('fa-check').addClass('fa-minus');
            text.text(text.data('hide-text'));
        } else {
            $('#inactiveRolesToggle').removeClass('active');
            icon.removeClass('fa-minus').addClass('fa-check');
            text.text(text.data('show-text'));
        }
    }

    filterInactiveRoles(showInactive) {
        const table = $('#listTable').DataTable();

        if (showInactive) {
            table.rows().every(function () {
                $(this.node()).show();
            });
        } else {
            table.rows().every(function () {
                const row = $(this.node());
                if (row.find('td:first').data('status') === 'inactive') {
                    row.hide();
                }
            });
        }

        table.draw(false); // Redraw without resetting pagination
        table.state.save();
    }

    loadRolesFragmentWhenReady() {
        // Don't empty #roles_menu — the overlay sits on top of the existing fragment so the
        // page height and scroll position are preserved while the queue drains.
        const spinner = new QueueDrainService('#roles_menu', { messageId: this.user, message: this.txtQueueSpinner });
        spinner.show();
        spinner.watch(() => this.loadRolesFragment());
    }

    loadAddUserRoleFragmentWhenReady() {
        const spinner = new QueueDrainService('#roles_menu', { messageId: this.user, message: this.txtQueueSpinner });
        spinner.show();
        spinner.watch(() => this.loadAddUserRoleFragment());
    }

    loadAddUserRoleFragment() {
        $("#roles_menu").load(this.UIUrl + this.user + "/addUserRole", () => {
            const columnDefOptions = [
                {
                    targets: [0],
                    data: 'name',
                    render: (data, type, row) => {
                        if (type !== 'display') {
                            return data;
                        }
                        let html = '<div>';
                        html += '<div>';
                        html += `<span>${data}</span>`;
                        html += '</div>';
                        html += row.alreadyAssigned ? '<div style="font-size: smaller; color: red;">Tildelt</div>' : '';
                        html += '</div>';
                        return html;
                    }
                },
                {
                    targets: [1],
                    data: 'itSystemName',
                    render: (data) => data
                },
                {
                    targets: [2],
                    data: 'description',
                    className: "preformat"
                },
                {
                    targets: [3],
                    data: 'id',
                    orderable: false,
                    searchable: false,
                    sortable: false,
                    render: (data, type, row) => {
                        if (!data || !row.assignable || row.assignable === 'false') {
                            return null;
                        }
                        return `<a href="#" class="js-add-user-role" data-roleid="${data}" data-systemtype="${row.itSystemType}" data-name="${row.name}"><em class="fa fa-plus"></em></a>`;
                    }
                }
            ];

            const table = new DatatableService().initDefaultServersideTable('#listTable_u_ur', `${this.restUrl}${this.user}/available`, columnDefOptions);

            const searchInput = table.table().container().querySelector('.dataTables_filter input');
            searchInput.focus();
        });
    }

    loadAddRoleGroupFragmentWhenReady() {
        const spinner = new QueueDrainService('#roles_menu', { messageId: this.user, message: this.txtQueueSpinner });
        spinner.show();
        spinner.watch(() => this.loadAddRoleGroupFragment());
    }

    loadAddRoleGroupFragment() {
        $("#roles_menu").load(this.UIUrl + this.user + "/addRoleGroup", () => {
            fragShowDataTableFun('#listTable_u_rg', 0, 100, "user_manage_add_role_group_table");
        });
    }

    requestRoleRemoval(elem) {
        const parent = elem.parentElement;
        const type = parent.dataset.type;
        const orgUnitUuid = parent.dataset.ouuuid;
        const roleId = parent.dataset.roleid;

        requestRoleModalService.setSuccessHandler(() => {
            window.location.href = this.reqRedirectUri;
        });

        if (type === 'ROLEGROUP') {
            requestRoleModalService.showRequestRoleGroupRemovalDialog(orgUnitUuid, this.user, roleId);
        } else {
            requestRoleModalService.showRequestRoleRemovalDialog(orgUnitUuid, this.user, roleId);
        }
    }

    deleteRoleAssignment(elem) {
        const parent = elem.parentElement;
        const assignmentId = parent.dataset.assignmentid;
        const assignedThrough = parent.dataset.assignedthrough;
        const type = parent.dataset.type;
        const roleName = parent.dataset.name;

        const endpoint = `${this.restUrl}${this.user}/removeassignment/${type}/${assignedThrough}/${assignmentId}`;

        const titleTxt = `${this.deleteRoleAssignmentTitleTxt}"${roleName}"`;
        const bodyTxt = this.deleteRoleAssignmentBodyTxt;
        const errorMessage = this.deleteRoleAssignmentErrorMessage;

        swal({
            html: true,
            title: titleTxt,
            text: bodyTxt,
            type: "warning",
            showCancelButton: true,
            confirmButtonColor: "#DD6B55",
            confirmButtonText: this.deleteAssignmentConfirmTxt,
            cancelButtonText: this.deleteAssignmentCancelTxt,
            closeOnConfirm: true,
            closeOnCancel: true
        }, (isConfirm) => {
            if (isConfirm) {
                $.ajax({
                    url: endpoint,
                    method: "POST",
                    headers: {
                        'X-CSRF-TOKEN': window.token
                    },
                    error: errorHandler(errorMessage),
                    success: () => {
                        this.loadRolesFragmentWhenReady();
                    }
                });
            }
        });
    }

    // Selects across ALL rows, not just the currently rendered page — DataTables
    // detaches other pages' <tr> nodes from the live DOM, so plain jQuery
    // selectors like $(".js-bulk-remove-role-checkbox") silently miss them.
    allBulkRemoveCheckboxes() {
        return $($('#listTable').DataTable().rows().nodes()).find(".js-bulk-remove-role-checkbox");
    }

    toggleBulkRemoveMode() {
        this.bulkRemoveModeActive = !this.bulkRemoveModeActive;

        this.allBulkRemoveCheckboxes().prop("checked", false).closest(".c-checkbox").toggle(this.bulkRemoveModeActive);
        $("#bulkRemoveRolesSubmitBtn").toggle(this.bulkRemoveModeActive);
        this.updateBulkRemoveSelectedCount();
    }

    updateBulkRemoveSelectedCount() {
        const count = this.allBulkRemoveCheckboxes().filter(":checked").length;
        $("#bulkRemoveRolesSelectedCount").text(`(${count} ${this.selectedCountLabel})`);
        $("#bulkRemoveRolesSubmitBtn").prop("disabled", count === 0);
    }

    submitBulkRemoveRoles() {
        const assignments = [];
        this.allBulkRemoveCheckboxes().filter(":checked").each((i, checkbox) => {
            const cell = checkbox.closest("td");
            assignments.push({
                type: cell.dataset.type,
                assignmentId: cell.dataset.assignmentid
            });
        });

        if (assignments.length === 0) {
            return;
        }

        swal({
            html: true,
            title: this.bulkRemoveTitleTxt,
            text: this.bulkRemoveBodyTxt,
            type: "warning",
            showCancelButton: true,
            confirmButtonColor: "#DD6B55",
            confirmButtonText: this.deleteAssignmentConfirmTxt,
            cancelButtonText: this.deleteAssignmentCancelTxt,
            closeOnConfirm: true,
            closeOnCancel: true
        }, (isConfirm) => {
            if (isConfirm) {
                $.ajax({
                    url: `${this.restUrl}${this.user}/bulkremoveassignments`,
                    contentType: "application/json",
                    method: "POST",
                    headers: {
                        'X-CSRF-TOKEN': window.token
                    },
                    data: JSON.stringify({ assignments }),
                    error: errorHandler(this.bulkRemoveErrorTxt),
                    success: () => {
                        this.bulkRemoveModeActive = false;
                        this.loadRolesFragmentWhenReady();
                    }
                });
            }
        });
    }

    editRoleAssignment(elem) {
        const parent = elem.parentElement;
        const assignmentId = parent.dataset.assignmentid;
        const type = parent.dataset.type;
        const startDate = parent.dataset.startdate;
        const stopDate = parent.dataset.stopdate;
        const assignedThrough = parent.dataset.assignedthrough;
        const orgUnit = parent.dataset.ouuuid;
        const caseNumber = parent.dataset.casenumber;
        const roleName = parent.dataset.name;

        userRoleEditModalService.showModal(this.user, startDate, stopDate, type, assignmentId, assignedThrough, orgUnit, this.possibleOrgUnits, caseNumber, roleName);
        userRoleEditModalService.loadPostponedConstraintsFragment(assignmentId, type, this.user);
    }

    addUserRole(elem) {
        const roleId = elem.dataset.roleid;
        const roleName = elem.dataset.name;

        window.modal.modal({
            backdrop: 'static',
            keyboard: false
        });

        userRoleModalService.loadPostponedConstraintsFragment(roleId);
        $('#stopDatePicker').data("DateTimePicker").clear();
        $('#startDatePicker').data("DateTimePicker").clear();
        $('#startDatePicker').data("DateTimePicker").date(new Date());
        $('#notifyId').attr('checked', false);
        $("#assignUserRoleName").text(roleName);

        window.modal.attr("roleid", roleId);
        window.modal.attr("userUuid", this.user);
        userRoleModalService.setManual(elem.dataset.systemtype === 'MANUAL');
    }

    addRoleGroup(elem) {
        const groupId = elem.dataset.rolegroupid;
        const groupName = elem.dataset.name;

        window.modalGroups.modal({
            backdrop: 'static',
            keyboard: false
        });

        $('#modal-groups-alert').text('');
        $('#modal-groups-alert').hide();

        $('#groupStopDatePicker').data("DateTimePicker").clear();
        $('#groupStartDatePicker').data("DateTimePicker").clear();
        $('#groupStartDatePicker').data("DateTimePicker").date(new Date());
        $('#assignRoleGroupName').text(groupName);

        window.modalGroups.attr("rolegroupid", groupId);
        window.modalGroups.attr("userUuid", this.user);
    }
}

document.addEventListener('DOMContentLoaded', () => {
    const config = JSON.parse(document.getElementById('user-manage-config').textContent);

    const csrfMeta = document.querySelector("meta[name='_csrf']");
    window.token = csrfMeta ? csrfMeta.getAttribute('content') : null;

    window.modal = $("#modal-positions");
    window.modalGroups = $("#modal-groups");

    const rolesService = new RolesService(config);
    window.rolesService = rolesService;
    window.datatableService = new DatatableService();

    $('#stamdataLink').on('click', () => {
        $('#caretIcon').toggleClass('fa-caret-right');
        $('#caretIcon').toggleClass('fa-caret-down');
    });

    // flip active tab on page-load
    const selectedTab = localStorage.getItem(config.user);
    if (selectedTab != null) {
        $(`a[data-toggle="tab"][href="${selectedTab}"]`).tab('show');
    }

    // setup tab memory
    $('#dataTabs a').on('click', function (event) {
        event.preventDefault();
        $(this).tab('show');
    });

    $('a[data-toggle="tab"]').on("shown.bs.tab", (event) => {
        const id = $(event.target).attr("href");
        localStorage.setItem(config.user, id);
    });

    // Initialize tabs
    if (config.stale) {
        const spinner = new QueueDrainService('#roles_menu', { messageId: config.user, message: config.txtQueueSpinner });
        spinner.show();
        spinner.watch(() => rolesService.loadRolesFragment());
    } else {
        rolesService.loadRolesFragment();
    }

    // Init userrole edit modal
    userRoleEditModalService.parentService = rolesService;
    userRoleModalService.parentService = rolesService;
    roleGroupModalService.parentService = rolesService;

    // Delegated listeners for content loaded dynamically into #roles_menu
    // (the roles table, and the "add role"/"add role group" sub-views).
    document.addEventListener('click', (event) => {
        const addUserRoleLink = event.target.closest('.js-add-user-role');
        if (addUserRoleLink) {
            rolesService.addUserRole(addUserRoleLink);
        }

        const toggleColumnLink = event.target.closest('.js-toggle-column');
        if (toggleColumnLink) {
            // Preserves the original `onclick="return dataTablesToggleColumn(this);"`
            // semantics: only cancel navigation if the function itself returns false.
            const result = dataTablesToggleColumn(toggleColumnLink);
            if (result === false) {
                event.preventDefault();
            }
        }

        const showAddUserRoleTrigger = event.target.closest('.js-show-add-user-role');
        if (showAddUserRoleTrigger) {
            rolesService.loadAddUserRoleFragment();
        }

        const showAddRoleGroupTrigger = event.target.closest('.js-show-add-role-group');
        if (showAddRoleGroupTrigger) {
            rolesService.loadAddRoleGroupFragment();
        }

        const editRoleAssignmentLink = event.target.closest('.js-edit-role-assignment');
        if (editRoleAssignmentLink) {
            rolesService.editRoleAssignment(editRoleAssignmentLink);
        }

        const deleteRoleAssignmentLink = event.target.closest('.js-delete-role-assignment');
        if (deleteRoleAssignmentLink) {
            rolesService.deleteRoleAssignment(deleteRoleAssignmentLink);
        }

        const toggleBulkRemoveLink = event.target.closest('.js-toggle-bulk-remove-roles');
        if (toggleBulkRemoveLink) {
            event.preventDefault();
            rolesService.toggleBulkRemoveMode();
        }

        const bulkRemoveSubmitBtn = event.target.closest('.js-bulk-remove-roles-submit');
        if (bulkRemoveSubmitBtn) {
            rolesService.submitBulkRemoveRoles();
        }

        const backToRolesLink = event.target.closest('.js-back-to-roles');
        if (backToRolesLink) {
            event.preventDefault();
            rolesService.loadRolesFragment();
        }

        const addRoleGroupRowLink = event.target.closest('.js-add-role-group-row');
        if (addRoleGroupRowLink) {
            event.preventDefault();
            rolesService.addRoleGroup(addRoleGroupRowLink);
        }
    });

    document.addEventListener('change', (event) => {
        if (event.target.classList.contains('js-bulk-remove-role-checkbox')) {
            rolesService.updateBulkRemoveSelectedCount();
        }
    });
});
