/**
 * Bootstraps the employee request wizard's employee list page: reads page
 * config and wires up the employee table.
 */
document.addEventListener("DOMContentLoaded", () => {
    const config = JSON.parse(document.getElementById("employees-config").textContent);

    // Same source as before (meta tag), just no longer read via jQuery/inline script.
    window.token = document.querySelector("meta[name='_csrf']").getAttribute("content");

    window.datatableService = window.datatableService || new DatatableService();
    window.expandableRoleGroupTableService = window.expandableRoleGroupTableService || new ExpandableRoleGroupTableService(config.detailsUrl);

    const employeeService = new EmployeeService(config, window.datatableService, window.expandableRoleGroupTableService);
    employeeService.init();
});
