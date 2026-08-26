document.addEventListener("DOMContentLoaded", () => {
    document.addEventListener("click", (event) => {
        const toggleColumnLink = event.target.closest(".js-toggle-column");
        if (toggleColumnLink) {
            event.preventDefault();
            dataTablesToggleColumn(toggleColumnLink);
            return;
        }

        const addUserRoleLink = event.target.closest(".js-load-add-user-role");
        if (addUserRoleLink) {
            event.preventDefault();
            window.rolesService.loadAddUserRoleFragment();
            return;
        }

        const addRoleGroupLink = event.target.closest(".js-load-add-role-group");
        if (addRoleGroupLink) {
            event.preventDefault();
            window.rolesService.loadAddRoleGroupFragment();
            return;
        }

        const editAssignmentLink = event.target.closest(".js-edit-role-assignment");
        if (editAssignmentLink) {
            event.preventDefault();
            window.rolesService.editRoleAssignment(editAssignmentLink);
            return;
        }

        const deleteAssignmentLink = event.target.closest(".js-delete-role-assignment");
        if (deleteAssignmentLink) {
            event.preventDefault();
            window.rolesService.deleteRoleAssignment(deleteAssignmentLink);
            return;
        }

        const toggleBulkRemoveLink = event.target.closest(".js-toggle-bulk-remove-roles");
        if (toggleBulkRemoveLink) {
            event.preventDefault();
            window.rolesService.toggleBulkRemoveMode();
            return;
        }

        const bulkRemoveSubmitBtn = event.target.closest(".js-bulk-remove-roles-submit");
        if (bulkRemoveSubmitBtn) {
            window.rolesService.submitBulkRemoveRoles();
            return;
        }

        const backToRolesButton = event.target.closest(".js-back-to-roles");
        if (backToRolesButton) {
            window.rolesService.loadRolesFragment();
            return;
        }

        const addRoleAssignmentLink = event.target.closest(".js-add-role-assignment");
        if (addRoleAssignmentLink) {
            event.preventDefault();
            window.rolesService.addRoleAssignment(addRoleAssignmentLink);
        }
    });

    document.addEventListener("change", (event) => {
        if (event.target.classList.contains("js-bulk-remove-role-checkbox")) {
            window.rolesService.updateBulkRemoveSelectedCount();
        }
    });
});
