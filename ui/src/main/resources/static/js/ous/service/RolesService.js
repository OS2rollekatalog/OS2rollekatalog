/**
 * Handles loading and managing role assignments for an organisational unit
 */
class RolesService {
    constructor(config, sweetAlertService) {
        this.config = config;
        this.sweetAlertService = sweetAlertService;
        this.bulkRemoveModeActive = false;
    }

    loadRolesFragment() {
        $("#roles_menu").load(this.config.urlUi + this.config.ou + "/roles", () => {
            // shared fragment scripts (datatables.html) read this from global scope
            window.currentTable = fragShowDataTableFun('#listTable', 0, 100, "ou_manage_list_of_roles");
            dataTablesRefreshIcons($('#dataTableDropdown'));
        });
    }

    loadAddUserRoleFragment() {
        $("#roles_menu").load(this.config.urlUi + this.config.ou + "/addUserRole", () => {
            fragShowDataTableFun('#listTable_ou_ur', 0, 100, "ou_manage_add_user_role_table");
        });
    }

    loadAddRoleGroupFragment() {
        $("#roles_menu").load(this.config.urlUi + this.config.ou + "/addRoleGroup", () => {
            fragShowDataTableFun('#listTable_ou_rg', 0, 100, "ou_manage_add_role_group_table");
        });
    }

    deleteRoleAssignment(elem) {
        const parent = elem.parentElement;
        const assignmentId = parent.dataset.assignmentid;
        const type = parent.dataset.objtype;
        const roleName = parent.dataset.name;

        const endpoint = this.config.url + this.config.ou + '/removeassignment/' + type + '/' + assignmentId;
        const titleTxt = this.config.deleteRoleAssignmentTitleTxt + '"' + roleName + '"';

        this.sweetAlertService.confirm(
            titleTxt,
            this.config.deleteRoleAssignmentBodyTxt,
            this.config.deleteAssignmentConfirmTxt,
            this.config.deleteAssignmentCancelTxt,
            () => {
                $.ajax({
                    url: endpoint,
                    method: "POST",
                    headers: {
                        'X-CSRF-TOKEN': window.token
                    },
                    error: errorHandler(this.config.deleteRoleAssignmentErrorMessage),
                    success: () => {
                        this.loadRolesFragment();
                    }
                });
            }
        );
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
        $("#bulkRemoveRolesSelectedCount").text(`(${count} ${this.config.selectedCountLabel})`);
        $("#bulkRemoveRolesSubmitBtn").prop("disabled", count === 0);
    }

    submitBulkRemoveRoles() {
        const assignments = [];
        this.allBulkRemoveCheckboxes().filter(":checked").each((i, checkbox) => {
            const cell = checkbox.closest("td");
            assignments.push({
                type: cell.dataset.objtype,
                assignmentId: cell.dataset.assignmentid
            });
        });

        if (assignments.length === 0) {
            return;
        }

        this.sweetAlertService.confirm(
            this.config.bulkRemoveTitleTxt,
            this.config.bulkRemoveBodyTxt,
            this.config.deleteAssignmentConfirmTxt,
            this.config.deleteAssignmentCancelTxt,
            () => {
                $.ajax({
                    url: `${this.config.url}${this.config.ou}/bulkremoveassignments`,
                    contentType: "application/json",
                    method: "POST",
                    headers: {
                        'X-CSRF-TOKEN': window.token
                    },
                    data: JSON.stringify({ assignments }),
                    error: errorHandler(this.config.bulkRemoveErrorTxt),
                    success: () => {
                        this.bulkRemoveModeActive = false;
                        this.loadRolesFragment();
                    }
                });
            }
        );
    }
}
