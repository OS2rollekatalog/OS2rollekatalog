/**
 * Bootstraps the removal-request wizard page: reads page config, sets up
 * shared mutable wizard state, and wires up the wizard's services.
 */
document.addEventListener("DOMContentLoaded", () => {
    const config = JSON.parse(document.getElementById("remove-wizard-config").textContent);

    // Same source as before (meta tag), just no longer read via jQuery/inline script.
    window.token = document.querySelector("meta[name='_csrf']").getAttribute("content");

    // ExpandableRoleGroupTableService (common.js) reads "detailsUrl" as a bare global
    // internally, same as on the dashboard page (index.js) - must stay exposed on
    // window for that shared service to keep working here too.
    window.detailsUrl = config.detailsUrl;

    // Mutable state shared across the wizard's services for the lifetime of the page.
    const state = {
        chosenRolesDTOs: [],
        chosenUserRoleIds: [],
        chosenRoleGroupIds: []
    };

    window.datatableService = window.datatableService || new DatatableService();
    window.expandableRoleGroupTableService = window.expandableRoleGroupTableService || new ExpandableRoleGroupTableService(config.detailsUrl);

    const roleGroupService = new RoleGroupService(config, state, window.datatableService, window.expandableRoleGroupTableService);
    const userRoleService = new UserRoleService(config, state, window.datatableService);
    const combinedService = new CombinedRoleService(config, state, window.datatableService, roleGroupService, userRoleService, window.expandableRoleGroupTableService);
    const roleService = new RoleService(config, state, roleGroupService, userRoleService, combinedService);
    const requestService = new RequestService(config, state, roleService);

    // Exposed in case not-yet-refactored fragments still reference these by
    // their old global names.
    window.requestService = requestService;
    window.roleService = roleService;
    window.roleGroupService = roleGroupService;
    window.userRoleService = userRoleService;
    window.combinedService = combinedService;

    requestService.init();
});
