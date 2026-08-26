/**
 * Bootstraps the role request wizard page: reads page config, sets up shared
 * mutable wizard state, and wires up the wizard's services.
 */
document.addEventListener("DOMContentLoaded", () => {
    const config = JSON.parse(document.getElementById("wizard-config").textContent);

    // Same source as before (meta tag), just no longer read via jQuery/inline script.
    window.token = document.querySelector("meta[name='_csrf']").getAttribute("content");

    // KleConstraintService / OrgUnitPostponedConstraintService (not yet refactored)
    // read "kleList" and "treeOUs" as bare globals internally, same as before.
    window.kleList = config.kleList;
    window.treeOUs = config.treeOUs;

    // Mutable state shared across the wizard's services for the lifetime of the page.
    const state = {
        chosenEmploymentId: null,
        chosenEmploymentChanged: false,
        chosenRolesDTOs: [],
        chosenUserRoleIds: [],
        chosenRoleGroupIds: []
    };

    window.datatableService = window.datatableService || new DatatableService();

    // UserRoleService and ConstraintService depend on each other, so
    // userRoleService is constructed first and wired to constraintService
    // once both instances exist.
    const userRoleService = new UserRoleService(config, state, window.datatableService, null);
    const constraintService = new ConstraintService(config, userRoleService);
    userRoleService.constraintService = constraintService;

    const roleGroupService = new RoleGroupService(config, state, window.datatableService);
    const combinedService = new CombinedService(config, state, window.datatableService, roleGroupService, userRoleService, constraintService);
    const existingRolesService = new ExistingRolesService(config, state, window.datatableService);
    const roleFragmentService = new RoleFragmentService(config, state, roleGroupService, userRoleService, combinedService, existingRolesService);
    const requestService = new RequestService(config, state, roleFragmentService, constraintService);

    // Kept for parity with the original page setup.
    window.networkService = new NetworkService(window.token);

    // Exposed in case not-yet-refactored fragments (e.g. the user-role constraint
    // modal) still reference these by their old global names.
    window.requestService = requestService;
    window.roleFragmentService = roleFragmentService;
    window.roleGroupService = roleGroupService;
    window.userRoleService = userRoleService;
    window.constraintService = constraintService;
    window.combinedService = combinedService;
    window.existingRolesService = existingRolesService;

    requestService.init();
});
