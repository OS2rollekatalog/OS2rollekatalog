function initOuRolesModal() {
    const ouRolesModalConfig = JSON.parse(document.getElementById("ou-roles-modal-config").textContent);

    const ouRoleAssignmentService = new OuRoleAssignmentService(ouRolesModalConfig, window.jsTreeService, window.notificationService);
    const ouRolesModalService = new OuRolesModalService(ouRolesModalConfig, ouRoleAssignmentService);
    const ouRolesEditModalService = new OuRolesEditModalService(ouRolesModalConfig, ouRoleAssignmentService);

    // Exposed on window so that: (1) the delegated listeners below can reach them, (2) dynamically
    // loaded fragments (addUserRole, addRoleGroup, ouAssignedRoles) can call them, and (3) each
    // page's own bootstrap script can set ouUuid/parentService on them. This runs synchronously,
    // as soon as the browser parses this script tag, since the config and service scripts above it
    // have already been parsed by that point - no need to wait for DOMContentLoaded.
    window.ouRoleAssignmentService = ouRoleAssignmentService;
    window.ouRolesModalService = ouRolesModalService;
    window.ouRolesEditModalService = ouRolesEditModalService;

    ouRoleAssignmentService.init();

    // The OU roles modal markup is sometimes embedded directly in the page (e.g. ous/manage.html)
    // and sometimes loaded dynamically via AJAX into a placeholder div (e.g. userroles/edit.html,
    // rolegroup/edit.html). Delegated listeners on `document` handle both cases, since they work
    // regardless of when the modal markup enters the DOM.
    document.addEventListener("click", (event) => {
        if (event.target.closest('.js-close-ou-roles-modal')) {
            ouRoleAssignmentService.closeModal();
            return;
        }

        if (event.target.closest('#assignInheritedWithExceptionsButton')) {
            ouRoleAssignmentService.handleInheritedWithExceptionsAssignment();
            return;
        }
        if (event.target.closest('#titleAssignTitlesButton')) {
            ouRoleAssignmentService.handleTitleRoleAssignment();
            return;
        }
        if (event.target.closest('#assignWithExceptionsButton')) {
            ouRoleAssignmentService.handleExceptedRoleAssignment();
            return;
        }
        if (event.target.closest('#assignWithTitlesAndExceptionsButton')) {
            ouRoleAssignmentService.handleTitleAndExceptedRoleAssignment();
            return;
        }
        if (event.target.closest('#assignPositiveWithInheritanceButton')) {
            ouRoleAssignmentService.handlePositiveInheritedTitleAssignment();
            return;
        }
        if (event.target.closest('#assignNegativeWithInheritanceButton')) {
            ouRoleAssignmentService.handleNegativeAssignment(true);
            return;
        }
        if (event.target.closest('#assignNegativeWithoutInheritanceButton')) {
            ouRoleAssignmentService.handleNegativeAssignment(false);
            return;
        }

        if (event.target.closest('#assignEveryoneChoiceDropdownForTestCases')) {
            ouRoleAssignmentService.handleEveryoneRoleAssignment(false);
            return;
        }
        if (event.target.closest('#assignEveryoneWithInheritanceChoice')) {
            ouRoleAssignmentService.handleEveryoneRoleAssignment(true);
            return;
        }

        if (event.target.closest('#assignManagerOnlyButton')) {
            ouRoleAssignmentService.handleManagerRoleAssignment(false, false);
            return;
        }
        if (event.target.closest('#assignManagerWithInheritanceButton')) {
            ouRoleAssignmentService.handleManagerRoleAssignment(false, true);
            return;
        }
        if (event.target.closest('#assignManagerAndSubstitutesButton')) {
            ouRoleAssignmentService.handleManagerRoleAssignment(true, false);
            return;
        }
        if (event.target.closest('#assignManagerAndSubstitutesWithInheritanceButton')) {
            ouRoleAssignmentService.handleManagerRoleAssignment(true, true);
            return;
        }

        if (event.target.closest('#assignFunctionAllButton')) {
            ouRoleAssignmentService.handleFunctionRoleAssignment(false);
            return;
        }
        if (event.target.closest('#assignFunctionAllWithInheritanceButton')) {
            ouRoleAssignmentService.handleFunctionRoleAssignment(true);
        }
    });
}

initOuRolesModal();
