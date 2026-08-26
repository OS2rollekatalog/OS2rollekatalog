/**
 * Drives the role-request wizard's steps: employment selection, role
 * selection, reason/timeframe, and final confirmation & submission.
 */
class RequestService {
    constructor(config, state, roleFragmentService, constraintService) {
        this.config = config;
        this.state = state;
        this.roleFragmentService = roleFragmentService;
        this.constraintService = constraintService;
        this.tabService = new TabService('rolerequest_wizard_roleselection');
        this.startDateObj = null;
        this.stopDateObj = null;
    }

    init() {
        $("#requestWizard").steps({
            autoFocus: true,
            headerTag: "h2",
            bodyTag: "section",
            transitionEffect: "slideLeft",
            onStepChanging: (event, currentIndex, newIndex) => {
                // Always allow the "previous" action
                if (currentIndex > newIndex) {
                    return true;
                }

                // Forbid "next" if no employment has been chosen
                if (currentIndex === 0 && this.state.chosenEmploymentId == null) {
                    return false;
                }

                // Forbid "next" if no roles have been chosen
                if (currentIndex === 1 && this.state.chosenRolesDTOs.length === 0) {
                    return false;
                }

                // Forbid "next" if no reason given and reasonSetting is OBLIGATORY
                if (currentIndex === 2 && $("#reason").val() === "" && this.config.reasonSetting === "OBLIGATORY") {
                    return false;
                }

                return true;
            },
            onStepChanged: (event, currentIndex, priorIndex) => {
                if (currentIndex === 0) {
                    $('input[name="employmentId"]').off();
                    $('input[name="employmentId"]').on('ifChecked', (event) => {
                        this.state.chosenEmploymentId = event.target.value;
                        this.state.chosenEmploymentChanged = true;
                        $("#confirmFor").text(event.target.dataset.position + " i " + event.target.dataset.ou);
                    });
                }

                if (priorIndex === 0 && currentIndex === 1) {
                    if (this.state.chosenEmploymentChanged) {
                        this.state.chosenRolesDTOs = [];
                        this.state.chosenUserRoleIds = [];
                        this.state.chosenRoleGroupIds = [];

                        $("#roleListPlaceholder").load(`${this.config.baseUrl}/roles?user=${this.config.userUuid}&position=${this.state.chosenEmploymentId}`, () => {
                            this.roleFragmentService.init();
                            this.state.chosenEmploymentChanged = false;

                            // Initialize tab restoration
                            this.tabService.restoreTab();
                            const roleListContainer = document.querySelector("#roleListPlaceholder");
                            const navLinks = roleListContainer.querySelectorAll('a.nav-link');
                            for (const link of navLinks) {
                                link.addEventListener('click', () => this.tabService.rememberTab(link.href.split('#')[1]));
                            }
                        });
                    }
                }

                if (this.config.reasonSetting === "NONE") {
                    if (currentIndex === 2) {
                        if (priorIndex === 3) {
                            $("#requestWizard").steps("previous");
                        } else {
                            $("#requestWizard").steps("next");
                        }
                    }
                }

                if (currentIndex === 3) {
                    // Clear existing rows in the table, if any
                    $("#confirmRolesTable tbody").empty();

                    // Iterate over chosenRolesDTOs and add rows
                    this.state.chosenRolesDTOs.forEach((role) => {
                        const rowHtml = `
                            <tr>
                                <td>${role.type}</td>
                                <td>${role.itSystem}</td>
                                <td>${role.name}</td>
                                <td>${role.approver}</td>
                            </tr>
                        `;
                        $("#confirmRolesTable tbody").append(rowHtml);
                    });

                    const reason = $("#reason").val();
                    $("#confirmReason").text(reason);

                    const startDate = $('#requestStartDate').val();
                    const stopDate = $('#requestEndDate').val();

                    this.roleFragmentService.validateDates(startDate, stopDate);

                    let timeFrameText;

                    if (stopDate) {
                        const stopParts = stopDate.split('/');
                        this.stopDateObj = new Date(stopParts[2], stopParts[1] - 1, stopParts[0]);
                    }
                    if (!startDate && !stopDate) {
                        timeFrameText = "Ikke angivet";
                    } else if (startDate && stopDate) {
                        timeFrameText = startDate + " - " + stopDate;
                    } else if (startDate) {
                        timeFrameText = startDate + " - ubegrænset";
                    } else {
                        timeFrameText = "nu - " + stopDate;
                    }

                    $('#confirmTimeFrame').text(timeFrameText);
                }
            },
            onFinishing: (event, currentIndex) => {
                const reason = $("#reason").val();
                if (this.state.chosenEmploymentId == null || this.state.chosenRolesDTOs.length === 0 || (reason === "" && this.config.reasonSetting === "OBLIGATORY")) {
                    return false;
                }

                return true;
            },
            onFinished: (event, currentIndex) => {
                const isoStartDate = this.roleFragmentService.convertToISODate($('#requestStartDate').val());
                const isoStopDate = this.roleFragmentService.convertToISODate($('#requestEndDate').val());

                const request = {
                    userUuid: this.config.userUuid,
                    positionId: this.state.chosenEmploymentId,
                    userRoles: this.state.chosenUserRoleIds,
                    roleGroups: this.state.chosenRoleGroupIds,
                    reason: $("#reason").val(),
                    constraints: this.constraintService.getConstraintsAsObjects(),
                    startDate: isoStartDate,
                    stopDate: isoStopDate
                };

                $.ajax({
                    url: `${this.config.restUrl}/wizard/save`,
                    contentType: 'application/json',
                    method: 'POST',
                    headers: {
                        'X-CSRF-TOKEN': window.token
                    },
                    data: JSON.stringify(request),
                    success: () => {
                        window.location = "/ui/request/myrequests";
                    },
                    error: defaultErrorHandler
                });
            },
            onInit: (event, currentIndex) => {
                $('.employeeRadioButton').iCheck({
                    checkboxClass: 'icheckbox_square-green',
                    radioClass: 'iradio_square-green',
                    increaseArea: '20%'
                });

                $('input[name="employmentId"]').on('ifChecked', (event) => {
                    this.state.chosenEmploymentId = event.target.value;
                    this.state.chosenEmploymentChanged = true;
                    $("#confirmFor").text(event.target.dataset.position + " i " + event.target.dataset.ou);
                });

                if (this.config.employments.length === 1) {
                    this.state.chosenEmploymentId = this.config.employments[0].id;
                    this.state.chosenEmploymentChanged = true;
                    $("#confirmFor").text(this.config.employments[0].position + " i " + this.config.employments[0].orgUnitName);
                    $("#requestWizard").steps("next");
                }
            },
            labels: {
                cancel: "Annuller",
                current: "Nuværende step:",
                pagination: "Paginering",
                finish: "Anmod",
                next: "Næste",
                previous: "Forrige",
                loading: "Loading ..."
            }
        });

        $('#requestStartDate').datepicker({
            todayBtn: "linked",
            keyboardNavigation: false,
            forceParse: false,
            calendarWeeks: true,
            autoclose: true,
            format: 'dd/mm/yyyy'
        }).on('changeDate', (selected) => {
            const minDate = new Date(selected.date.valueOf());
            $('#endDate').datepicker('setStartDate', minDate);
        });

        $('#requestEndDate').datepicker({
            todayBtn: "linked",
            keyboardNavigation: false,
            forceParse: false,
            calendarWeeks: true,
            autoclose: true,
            format: 'dd/mm/yyyy'
        });
    }
}

/**
 * Manages the role/role-group selection section of the wizard: initializing
 * the relevant DataTables (combined or split, depending on config), keeping
 * checkbox states in sync with the chosen selection, and refreshing tables
 * when the "hide already assigned" filter is toggled.
 */
class RoleFragmentService {
    constructor(config, state, roleGroupService, userRoleService, combinedService, existingRolesService) {
        this.config = config;
        this.state = state;
        this.roleGroupService = roleGroupService;
        this.userRoleService = userRoleService;
        this.combinedService = combinedService;
        this.existingRolesService = existingRolesService;

        /**
         * Holds subscriptions for refreshing of serverside tables. Contains objects with the properties:
         * table: the datatable instance
         * urlBuilder: function returning a URL pointing at the data endpoint for the table
         * @type {Set<{table: object, urlBuilder: Function}>}
         */
        this.tablesSubscribedForRefresh = new Set();
        this.startDateObj = null;
        this.stopDateObj = null;
    }

    init() {
        this.initHideAlreadyAssignedSelector();

        if (!this.config.showCombinedTable) {
            if (this.config.showRecommendedTab) {
                this.initRecommendedRoleGroupsTable();
                this.initRecommendedUserRolesTable();
            }
            if (this.config.showAllTab) {
                this.initAllUserRolesTable();
                this.initAllRolegroupsTable();
            }
        } else {
            this.initAllRolesAndRoleGroupsRecommendedTable();
            this.initAllRolesAndRoleGroupsTable();
        }

        if (this.config.showExistingTab) {
            this.initExistingRolesTable();
        }
    }

    async initExistingRolesTable() {
        const tableId = 'existingRolesTable';
        const table = this.existingRolesService.initExistingRoles(tableId);

        table.on('draw', () => {
            // Handle folding out of detail view (only for role groups)
            table.on('click', 'td.dt-control', (e) => {
                const rowData = table.row(e.target.closest('tr')).data();
                new DataTableService().toggleRowDetailsFromServer(e, table, `${this.config.detailsUrl}/${rowData.id}/userroles`);
            });
        });

        this.tablesSubscribedForRefresh.add({
            table,
            urlBuilder: () => this.existingRolesService.buildTableUrl()
        });
    }

    async initAllRolesAndRoleGroupsRecommendedTable() {
        const tableId = 'allRecommendedTable';
        const checkboxClass = 'allRecommendedCheckbox';
        const table = this.combinedService.initRecommendedTable(tableId);

        this.combinedService.initCombinedSelection(checkboxClass);

        table.on('draw', () => {
            $(`.${checkboxClass}`).iCheck({
                checkboxClass: 'icheckbox_square-green',
                radioClass: 'iradio_square-green'
            });

            this.combinedService.initCombinedSelection(checkboxClass);

            // Restore checkbox states for both user roles and role groups
            $('.allRecommendedCheckbox').each((index, element) => {
                const id = element.value;
                const isRoleGroup = $(element).hasClass('recommendedRoleGroupsCheckbox');

                if (isRoleGroup) {
                    if (!this.state.chosenRoleGroupIds.includes(id)) {
                        $(element).iCheck('uncheck');
                    } else {
                        $(element).iCheck('check');
                    }
                } else {
                    if (!this.state.chosenUserRoleIds.includes(id)) {
                        $(element).iCheck('uncheck');
                    } else {
                        $(element).iCheck('check');
                    }
                }
            });

            // Handle folding out of detail view (only for role groups)
            table.on('click', 'td.dt-control', (e) => {
                const rowData = table.row(e.target.closest('tr')).data();
                new DataTableService().toggleRowDetailsFromServer(e, table, `${this.config.detailsUrl}/${rowData.id}/userroles`);
            });
        });

        this.tablesSubscribedForRefresh.add({
            table,
            urlBuilder: () => this.combinedService.buildRecommendedTableUrl()
        });
    }

    async initAllRolesAndRoleGroupsTable() {
        const tableId = 'allCombinedTable';
        const checkboxClass = 'allCombinedCheckbox';
        const table = this.combinedService.initAllTable(tableId);

        this.combinedService.initCombinedSelection(checkboxClass);

        table.on('draw', () => {
            $(`.${checkboxClass}`).iCheck({
                checkboxClass: 'icheckbox_square-green',
                radioClass: 'iradio_square-green'
            });

            this.combinedService.initCombinedSelection(checkboxClass);

            // Restore checkbox states for both user roles and role groups
            $('.allCombinedCheckbox').each((index, element) => {
                const id = element.value;
                const isRoleGroup = $(element).hasClass('allRoleGroupsCheckbox');

                if (isRoleGroup) {
                    if (!this.state.chosenRoleGroupIds.includes(id)) {
                        $(element).iCheck('uncheck');
                    } else {
                        $(element).iCheck('check');
                    }
                } else {
                    if (!this.state.chosenUserRoleIds.includes(id)) {
                        $(element).iCheck('uncheck');
                    } else {
                        $(element).iCheck('check');
                    }
                }
            });

            // Handle folding out of detail view (only for role groups)
            table.on('click', 'td.dt-control', (e) => {
                const rowData = table.row(e.target.closest('tr')).data();
                new DataTableService().toggleRowDetailsFromServer(e, table, `${this.config.detailsUrl}/${rowData.id}/userroles`);
            });
        });

        this.tablesSubscribedForRefresh.add({
            table,
            urlBuilder: () => this.combinedService.buildTableUrl()
        });
    }

    async initRecommendedRoleGroupsTable() {
        const tableId = 'recommendedRoleGroupTable';
        const checkboxClass = 'recommendedRoleGroupsCheckbox';
        const table = this.roleGroupService.initRecommendedRoleGroups(tableId);
        this.roleGroupService.initRoleGroupSelection(checkboxClass);

        table.on('draw', () => {
            $(`.${checkboxClass}`).iCheck({
                checkboxClass: 'icheckbox_square-green',
                radioClass: 'iradio_square-green'
            });
            this.roleGroupService.initRoleGroupSelection(checkboxClass);

            $(`.${checkboxClass}`).each((index, element) => {
                const id = element.value;
                if (!this.state.chosenRoleGroupIds.includes(id)) {
                    $(element).iCheck('uncheck');
                } else {
                    $(element).iCheck('check');
                }
            });

            // Handle folding out of detail view
            table.on('click', 'td.dt-control', (e) => {
                const rowData = table.row(e.target.closest('tr')).data();
                new DataTableService().toggleRowDetailsFromServer(e, table, `${this.config.detailsUrl}/${rowData.id}/userroles`);
            });
        });

        this.tablesSubscribedForRefresh.add({
            table,
            urlBuilder: () => this.roleGroupService.buildRecommendedTableUrl()
        });
    }

    validateDates(startDate, stopDate) {
        if (startDate) {
            const startParts = startDate.split('/');
            this.startDateObj = new Date(startParts[2], startParts[1] - 1, startParts[0]);
        }

        if (stopDate) {
            const stopParts = stopDate.split('/');
            this.stopDateObj = new Date(stopParts[2], stopParts[1] - 1, stopParts[0]);
        }

        if (this.startDateObj && this.stopDateObj && this.startDateObj > this.stopDateObj) {
            $("#requestWizard").steps("previous");
            toastr.warning("Startdato skal være før eller lig med stopdato");
            return false;
        }
        return true;
    }

    async initRecommendedUserRolesTable() {
        const tableId = 'recommendedUserRoleTable';
        const checkboxClass = 'recommendedUserRolesCheckbox';
        const table = this.userRoleService.initRecommendedUserRoles(tableId);
        this.userRoleService.initUserRoleSelection(checkboxClass);

        table.on('draw', () => {
            $(`.${checkboxClass}`).iCheck({
                checkboxClass: 'icheckbox_square-green',
                radioClass: 'iradio_square-green'
            });
            this.userRoleService.initUserRoleSelection(checkboxClass);
        });

        this.tablesSubscribedForRefresh.add({
            table,
            urlBuilder: () => this.userRoleService.buildRecommendedTableUrl()
        });
    }

    convertToISODate(dateStr) {
        if (!dateStr || dateStr.trim() === '') {
            return null;
        }
        const parts = dateStr.split('/');
        return parts[2] + '-' + parts[1] + '-' + parts[0];
    }

    async initAllUserRolesTable() {
        const tableId = 'allUserRoleTable';
        const checkboxClass = 'allUserRolesCheckbox';
        const table = this.userRoleService.initAllUserroles(tableId);
        this.userRoleService.initUserRoleSelection(checkboxClass);

        table.on('draw', () => {
            $(`.${checkboxClass}`).iCheck({
                checkboxClass: 'icheckbox_square-green',
                radioClass: 'iradio_square-green'
            });
            this.userRoleService.initUserRoleSelection(checkboxClass);

            $(`.${checkboxClass}`).each((index, element) => {
                const id = element.value;
                if (!this.state.chosenUserRoleIds.includes(id)) {
                    $(element).iCheck('uncheck');
                } else {
                    $(element).iCheck('check');
                }
            });
        });

        this.tablesSubscribedForRefresh.add({
            table,
            urlBuilder: () => this.userRoleService.buildTableUrl()
        });
    }

    async initAllRolegroupsTable() {
        const tableId = 'allRoleGroupTable';
        const checkboxClass = 'allRoleGroupsCheckbox';
        const table = this.roleGroupService.initAllRolegroups(tableId);

        table.on('draw', () => {
            $(`.${checkboxClass}`).iCheck({
                checkboxClass: 'icheckbox_square-green',
                radioClass: 'iradio_square-green'
            });
            this.roleGroupService.initRoleGroupSelection(checkboxClass);

            $(`.${checkboxClass}`).each((index, element) => {
                const id = element.value;
                if (!this.state.chosenRoleGroupIds.includes(id)) {
                    $(element).iCheck('uncheck');
                } else {
                    $(element).iCheck('check');
                }
            });

            // Handle folding out of detail view
            table.on('click', 'td.dt-control', (e) => {
                const rowData = table.row(e.target.closest('tr')).data();
                new DataTableService().toggleRowDetailsFromServer(e, table, `${this.config.detailsUrl}/${rowData.id}/userroles`);
            });
        });

        this.tablesSubscribedForRefresh.add({
            table,
            urlBuilder: () => this.roleGroupService.buildTableUrl()
        });
    }

    initHideAlreadyAssignedSelector() {
        const hideAlreadyAssignedSelector = $('#hideAlreadyAssignedSelector');

        // Init iCheck
        hideAlreadyAssignedSelector.iCheck({
            checkboxClass: 'icheckbox_square-green',
            radioClass: 'iradio_square-green'
        });

        // Load state from sessionStorage
        const savedState = sessionStorage.getItem('requestwizard_hideAlreadyAssignedSelector');
        if (savedState === 'true' || savedState === null) {
            hideAlreadyAssignedSelector.iCheck('check');
        } else {
            hideAlreadyAssignedSelector.iCheck('uncheck');
        }
        // NOTE: pre-existing no-op - sets a "checked" property on the jQuery wrapper
        // object itself, not on the underlying DOM checkbox. Kept unchanged; see notes.
        hideAlreadyAssignedSelector.checked = savedState;

        // Ensure state is saved and tables are reloaded on state toggle
        hideAlreadyAssignedSelector.on('ifToggled', (event) => {
            const state = event.target.checked;
            sessionStorage.setItem('requestwizard_hideAlreadyAssignedSelector', state);
            this.refreshLoadedTables();
        });
    }

    /**
     * Refreshes serverside datatables that have subscribed to tablesSubscribedForRefresh
     */
    refreshLoadedTables() {
        for (const subscription of this.tablesSubscribedForRefresh) {
            subscription.table
                .ajax.url(subscription.urlBuilder())
                .load(null, true);
        }
    }
}

/**
 * Builds and manages the combined (role + role-group) DataTables used when
 * the combined-table feature is enabled for the tenant.
 */
class CombinedService {
    constructor(config, state, datatableService, roleGroupService, userRoleService, constraintService) {
        this.config = config;
        this.state = state;
        this.datatableService = datatableService;
        this.roleGroupService = roleGroupService;
        this.userRoleService = userRoleService;
        this.constraintService = constraintService;
    }

    initAllTable(tableId) {
        const combinedColumnDefs = [
            {
                targets: [0],
                data: 'id',
                orderable: false,
                searchable: false,
                visible: false
            },
            {
                targets: [1],
                data: 'type',
                orderable: false,
                searchable: false,
                render: (data, type, row, meta) => {
                    if (type === 'display') {
                        return data === 'roleGroup' ?
                            '<span class="badge" style="background-color: #0066cc; color: white;">Rollebuket</span>' :
                            '<span class="badge" style="background-color: #9933cc; color: white;">Jobfunktionsrolle</span>';
                    } else {
                        return data;
                    }
                }
            },
            // Expand/collapse control which is only used for role-groups
            {
                targets: [2],
                data: 'type',
                orderable: false,
                searchable: false,
                className: '',
                createdCell: (td, cellData, rowData, row, col) => {
                    if (rowData.type === 'roleGroup') {
                        $(td).addClass('dt-control');
                    }
                },
                render: () => ""
            },
            {
                targets: [3],
                data: 'id',
                orderable: false,
                searchable: false,
                render: (data, type, row, meta) => {
                    if (type === 'display') {
                        const isRoleGroup = row.type === 'roleGroup';
                        const className = isRoleGroup ? 'allRoleGroupsCheckbox' : 'allUserRolesCheckbox';
                        const idClass = isRoleGroup ? `roleGroups${data}` : `userRoles${data}`;

                        let dataAttrs = `data-name="${row.name}" data-approver="${row.approver}"`;

                        if (!isRoleGroup) {
                            dataAttrs += ` data-itsystem="${row.itSystemName}" data-has-constraints="${row.hasConstraints}"`;
                        }

                        return `<input type="checkbox" class="i-checks allCombinedCheckbox ${className} ${idClass}" value="${data}" ${dataAttrs}>`;
                    } else {
                        return data;
                    }
                }
            },
            {
                targets: [4],
                data: 'itSystemName',
                render: (data, type, row, meta) => {
                    if (type === 'display') {
                        if (row.type === 'roleGroup') {
                            return '-';
                        }

                        const alreadyAssignedHTML = row.alreadyAssigned ?
                            ` <span class="badge badge-warning">Brugeren har allerede rollen tildelt</span>` : '';
                        const hasConstraintsHTML = row.hasConstraints ?
                            ` <span class="badge badge-warning">Kræver valg af dataafgrænsninger</span>` : '';

                        return (data || '') + alreadyAssignedHTML + hasConstraintsHTML;
                    } else {
                        return data || '';
                    }
                }
            },
            {
                targets: [5],
                data: 'name',
                render: (data, type, row, meta) => {
                    if (type === 'display') {
                        if (row.type === 'roleGroup') {
                            const alreadyAssignedHTML = row.alreadyAssigned ?
                                ` <span class="badge badge-warning">Brugeren har allerede rollen tildelt</span>` : '';
                            return data + alreadyAssignedHTML;
                        }
                        return data;
                    } else {
                        return data;
                    }
                }
            },
            {
                targets: [6],
                data: 'description',
                orderable: false,
                className: "preformat"
            },
            {
                targets: [7],
                data: 'approver',
                orderable: false,
                searchable: false
            },
            {
                targets: [8],
                data: 'roleWithinRoleGroup',
                visible: false,
                searchable: true,
                orderable: false
            }
        ];

        return this.datatableService.initDefaultServersideTable(`#${tableId}`, this.buildTableUrl(), combinedColumnDefs, [[5, "asc"]]);
    }

    initRecommendedTable(tableId) {
        const combinedColumnDefs = [
            {
                targets: [0],
                data: 'id',
                orderable: false,
                searchable: false,
                visible: false
            },
            {
                targets: [1],
                data: 'type',
                orderable: false,
                searchable: false,
                render: (data, type, row, meta) => {
                    if (type === 'display') {
                        return data === 'roleGroup' ?
                            '<span class="badge" style="background-color: #0066cc; color: white;">Rollebuket</span>' :
                            '<span class="badge" style="background-color: #9933cc; color: white;">Jobfunktionsrolle</span>';
                    } else {
                        return data;
                    }
                }
            },
            // Expand/collapse control for role-groups
            {
                targets: [2],
                data: 'type',
                orderable: false,
                searchable: false,
                className: '',
                createdCell: (td, cellData, rowData, row, col) => {
                    if (rowData.type === 'roleGroup') {
                        $(td).addClass('dt-control');
                    }
                },
                render: () => ""
            },
            {
                targets: [3],
                data: 'id',
                orderable: false,
                searchable: false,
                render: (data, type, row, meta) => {
                    if (type === 'display') {
                        const isRoleGroup = row.type === 'roleGroup';
                        const className = isRoleGroup ? 'recommendedRoleGroupsCheckbox' : 'recommendedUserRolesCheckbox';
                        const idClass = isRoleGroup ? `roleGroups${data}` : `userRoles${data}`;

                        let dataAttrs = `data-name="${row.name}" data-approver="${row.approver}"`;

                        if (!isRoleGroup) {
                            dataAttrs += ` data-itsystem="${row.itSystemName}" data-has-constraints="${row.hasConstraints}"`;
                        }

                        return `<input type="checkbox" class="i-checks allRecommendedCheckbox ${className} ${idClass}" value="${data}" ${dataAttrs}>`;
                    } else {
                        return data;
                    }
                }
            },
            {
                targets: [4],
                data: 'itSystemName',
                render: (data, type, row, meta) => {
                    if (type === 'display') {
                        if (row.type === 'roleGroup') {
                            return '-';
                        }

                        const alreadyAssignedHTML = row.alreadyAssigned ?
                            ` <span class="badge badge-warning">Brugeren har allerede rollen tildelt</span>` : '';
                        const hasConstraintsHTML = row.hasConstraints ?
                            ` <span class="badge badge-warning">Kræver valg af dataafgrænsninger</span>` : '';

                        return (data || '') + alreadyAssignedHTML + hasConstraintsHTML;
                    } else {
                        return data || '';
                    }
                }
            },
            {
                targets: [5],
                data: 'name',
                render: (data, type, row, meta) => {
                    if (type === 'display') {
                        if (row.type === 'roleGroup') {
                            const alreadyAssignedHTML = row.alreadyAssigned ?
                                ` <span class="badge badge-warning">Brugeren har allerede rollen tildelt</span>` : '';
                            return data + alreadyAssignedHTML;
                        }
                        return data;
                    } else {
                        return data;
                    }
                }
            },
            {
                targets: [6],
                data: 'description',
                orderable: false,
                className: "preformat"
            },
            {
                targets: [7],
                data: 'approver',
                orderable: false
            },
            {
                targets: [8],
                data: 'roleWithinRoleGroup',
                visible: false,
                searchable: true,
                orderable: false
            }
        ];

        return this.datatableService.initDefaultClientSideTable(`#${tableId}`, this.buildRecommendedTableUrl(), [[5, "asc"]], combinedColumnDefs);
    }

    buildTableUrl() {
        const base = `${this.config.restUrl}/wizard/allcombined/${this.config.userUuid}`;
        const hideAlreadyAssignedCheckbox = document.getElementById("hideAlreadyAssignedSelector");
        const hideAlreadyAssigned = hideAlreadyAssignedCheckbox.checked;
        return `${base}?hideAlreadyAssigned=${hideAlreadyAssigned}&position=${this.state.chosenEmploymentId}`;
    }

    buildRecommendedTableUrl() {
        const base = `${this.config.restUrl}/wizard/recommendedcombined/${this.config.userUuid}`;
        const hideAlreadyAssignedCheckbox = document.getElementById("hideAlreadyAssignedSelector");
        const hideAlreadyAssigned = hideAlreadyAssignedCheckbox.checked;
        return `${base}?hideAlreadyAssigned=${hideAlreadyAssigned}&position=${this.state.chosenEmploymentId}`;
    }

    // Initialize checkbox selection behavior for the combined table
    initCombinedSelection(checkboxClass) {
        $('.' + checkboxClass).off();

        $('.' + checkboxClass).on('ifChecked', (event) => {
            const checkbox = event.target;
            const isRoleGroup = $(checkbox).hasClass('allRoleGroupsCheckbox') || $(checkbox).hasClass('recommendedRoleGroupsCheckbox');

            if (isRoleGroup) {
                this.roleGroupService.check(checkbox);
            } else {
                const hasConstraints = checkbox.dataset.hasConstraints;
                if (hasConstraints === 'true') {
                    const id = checkbox.value;
                    this.constraintService.loadModal(id, checkbox);
                } else {
                    this.userRoleService.check(checkbox);
                }
            }
        });

        $('.' + checkboxClass).on('ifUnchecked', (event) => {
            const checkbox = event.target;
            const isRoleGroup = $(checkbox).hasClass('allRoleGroupsCheckbox') || $(checkbox).hasClass('recommendedRoleGroupsCheckbox');

            if (isRoleGroup) {
                this.roleGroupService.uncheck(checkbox);
            } else {
                this.userRoleService.uncheck(checkbox);
            }
        });
    }
}

/**
 * Handles role-group tables (all / recommended) and the selection state
 * (checking/unchecking) for role groups in the wizard.
 */
class RoleGroupService {
    constructor(config, state, datatableService) {
        this.config = config;
        this.state = state;
        this.datatableService = datatableService;
    }

    initAllRolegroups(tableId) {
        const rolegroupColumnDefs = [
            {
                targets: [0],
                data: 'id',
                orderable: false,
                searchable: false,
                visible: false
            },
            {
                targets: [1],
                data: 'id',
                orderable: false,
                searchable: false,
                className: "dt-control",
                render: (data, type, row, meta) => {
                    if (type === 'display') {
                        return '';
                    } else {
                        return data;
                    }
                }
            },
            {
                targets: [2],
                data: 'id',
                orderable: false,
                searchable: false,
                render: (data, type, row, meta) => {
                    if (type === 'display') {
                        return `<input type="checkbox" class="i-checks allRoleGroupsCheckbox roleGroups${data}" value="${data}" data-name="${row.name}" data-approver="${row.approver}">`;
                    } else {
                        return data;
                    }
                }
            },
            {
                targets: [3],
                data: 'name',
                render: (data, type, row, meta) => {
                    if (type === 'display') {
                        const alreadyAssignedHTML = row.alreadyAssigned ? ` <span class="badge badge-warning">Brugeren har allerede rollen tildelt</span>` : '';
                        return data + alreadyAssignedHTML;
                    } else {
                        return data;
                    }
                }
            },
            {
                targets: [4],
                data: 'description',
                orderable: false,
                className: "preformat"
            },
            {
                targets: [5],
                data: 'approver',
                orderable: false
            }
        ];
        return this.datatableService.initDefaultClientSideTable(`#${tableId}`, this.buildTableUrl(), [[3, "asc"]], rolegroupColumnDefs);
    }

    initRecommendedRoleGroups(tableId) {
        const columns = [
            {
                targets: [0],
                data: 'id',
                orderable: false,
                searchable: false,
                visible: false
            },
            {
                targets: [1],
                data: 'id',
                orderable: false,
                searchable: false,
                className: "dt-control",
                render: (data, type, row, meta) => {
                    if (type === 'display') {
                        return '';
                    } else {
                        return data;
                    }
                }
            },
            {
                targets: [2],
                data: 'id',
                orderable: false,
                searchable: false,
                render: (data, type, row, meta) => {
                    if (type === 'display') {
                        return `<input type="checkbox" class="i-checks recommendedRoleGroupsCheckbox roleGroups${data}" value="${data}" data-name="${row.name}" data-approver="${row.approver}">`;
                    } else {
                        return data;
                    }
                }
            },
            {
                targets: [3],
                data: 'name',
                render: (data, type, row, meta) => {
                    if (type === 'display') {
                        const alreadyAssignedHTML = row.alreadyAssigned ? ` <span class="badge badge-warning">Brugeren har allerede rollen tildelt</span>` : '';
                        return data + alreadyAssignedHTML;
                    } else {
                        return data;
                    }
                }
            },
            {
                targets: [4],
                data: 'description',
                orderable: false,
                className: "preformat"
            },
            {
                targets: [5],
                data: 'approver',
                orderable: false
            }
        ];
        return this.datatableService.initDefaultClientSideTable(`#${tableId}`, this.buildRecommendedTableUrl(), [[3, "asc"]], columns);
    }

    buildRecommendedTableUrl() {
        const base = `${this.config.restUrl}/wizard/recommendedrolegroups/${this.config.userUuid}`;
        const hideAlreadyAssignedCheckbox = document.getElementById("hideAlreadyAssignedSelector");
        const hideAlreadyAssigned = hideAlreadyAssignedCheckbox.checked;
        return `${base}?hideAlreadyAssigned=${hideAlreadyAssigned}&position=${this.state.chosenEmploymentId}`;
    }

    buildTableUrl() {
        const base = `${this.config.restUrl}/wizard/allrolegroups/${this.config.userUuid}`;
        const hideAlreadyAssignedCheckbox = document.getElementById("hideAlreadyAssignedSelector");
        const hideAlreadyAssigned = hideAlreadyAssignedCheckbox.checked;
        return `${base}?hideAlreadyAssigned=${hideAlreadyAssigned}&position=${this.state.chosenEmploymentId}`;
    }

    // NOTE: pre-existing method, not called anywhere in this file - kept for parity.
    addCheckboxListeners(checkboxClass) {
        $('.' + checkboxClass).off();
        $('.' + checkboxClass).on('ifChanged', (event) => {
            this.check(event.target);
        });

        $('.' + checkboxClass).on('ifUnchecked', (event) => {
            this.uncheck(event.target);
        });
    }

    initRoleGroupSelection(checkboxClass) {
        $('.' + checkboxClass).off();

        $('.' + checkboxClass).on('ifChecked', (event) => {
            this.check(event.target);
        });

        $('.' + checkboxClass).on('ifUnchecked', (event) => {
            this.uncheck(event.target);
        });
    }

    check(checkbox) {
        const id = checkbox.value;
        const name = checkbox.dataset.name;
        const approver = checkbox.dataset.approver;
        const type = this.config.roleGroupText;

        $('.roleGroups' + id).each(function () {
            if (!this.checked) {
                $(this).iCheck('check');
            }
        });

        if (!this.state.chosenRoleGroupIds.includes(id)) {
            this.state.chosenRoleGroupIds.push(id);
        }

        const exists = this.state.chosenRolesDTOs.some((dto) => dto.id === id && dto.type === type);

        if (!exists) {
            this.state.chosenRolesDTOs.push({
                id,
                name,
                itSystem: "",
                approver,
                type
            });
        }
    }

    uncheck(checkbox) {
        const id = checkbox.value;
        const type = this.config.roleGroupText;

        $('.roleGroups' + id).each(function () {
            if (this.checked) {
                $(this).iCheck('uncheck');
            }
        });

        const idIndex = this.state.chosenRoleGroupIds.indexOf(id);
        if (idIndex !== -1) {
            this.state.chosenRoleGroupIds.splice(idIndex, 1);
        }

        this.state.chosenRolesDTOs = this.state.chosenRolesDTOs.filter((dto) => !(dto.id === id && dto.type === type));
    }
}

class ExistingRolesService {
    constructor(config, state, datatableService) {
        this.config = config;
        this.state = state;
        this.datatableService = datatableService;
    }

    initExistingRoles(tableId) {
        const columns = [
            {
                targets: [0],
                data: 'id',
                orderable: false,
                searchable: false,
                visible: false
            },
            {
                targets: [1],
                data: 'type',
                orderable: false,
                searchable: false,
                render: (data, type, row, meta) => {
                    if (type === 'display') {
                        return data === 'roleGroup' ?
                            '<span class="badge" style="background-color: #0066cc; color: white;">Rollebuket</span>' :
                            '<span class="badge" style="background-color: #9933cc; color: white;">Jobfunktionsrolle</span>';
                    } else {
                        return data;
                    }
                }
            },
            // Expand/collapse control which is only used for role-groups
            {
                targets: [2],
                data: 'type',
                orderable: false,
                searchable: false,
                className: '',
                createdCell: (td, cellData, rowData, row, col) => {
                    if (rowData.type === 'roleGroup') {
                        $(td).addClass('dt-control');
                    }
                },
                render: () => ""
            },
            {
                targets: [3],
                data: 'itSystemName',
                render: (data, type, row, meta) => {
                    if (type === 'display') {
                        return row.type === 'roleGroup' ? '-' : (data || '');
                    } else {
                        return data || '';
                    }
                }
            },
            {
                targets: [4],
                data: 'name'
            },
            {
                targets: [5],
                data: 'description',
                orderable: false,
                className: "preformat"
            },
            {
                targets: [6],
                data: 'approver',
                orderable: false,
                searchable: false
            },
            {
                targets: [7],
                data: 'roleWithinRoleGroup',
                visible: false,
                searchable: true,
                orderable: false
            }
        ];
        return this.datatableService.initDefaultClientSideTable(`#${tableId}`, this.buildTableUrl(), [[4, "asc"]], columns);
    }

    buildTableUrl() {
        return `${this.config.restUrl}/wizard/existingroles/${this.config.userUuid}`;
    }
}

/**
 * Handles user-role tables (all / recommended) and the selection state
 * (checking/unchecking, including routing constrained roles to the
 * constraint modal) for user roles in the wizard.
 */
class UserRoleService {
    constructor(config, state, datatableService, constraintService) {
        this.config = config;
        this.state = state;
        this.datatableService = datatableService;
        this.constraintService = constraintService;
    }

    // NOTE: pre-existing method, not called anywhere in this file - kept for parity.
    init(tableId) {
        $(`#${tableId}`).DataTable({
            pageLength: 25,
            responsive: true,
            autoWidth: false,
            columnDefs: [{ orderable: false, targets: [0, 1] }],
            order: [[2, "asc"]],
            language: {
                search: "Søg",
                lengthMenu: "_MENU_ rækker per side",
                info: "Viser _START_ til _END_ af _TOTAL_ rækker",
                zeroRecords: "Ingen data...",
                infoEmpty: "",
                infoFiltered: "(ud af _MAX_ rækker)",
                paginate: {
                    previous: "Forrige",
                    next: "Næste"
                }
            }
        });
    }

    initAllUserroles(tableId) {
        const userroleColumnDefs = [
            {
                targets: [0],
                data: 'id',
                orderable: false,
                searchable: false,
                render: (data, type, row, meta) => {
                    if (type === 'display') {
                        return `<input type="checkbox" class="i-checks allUserRolesCheckbox userRoles${data}" value="${data}" data-name="${row.name}" data-approver="${row.approver}" data-itsystem="${row.itSystemName}" data-has-constraints="${row.hasConstraints}"></input>`;
                    } else {
                        return data;
                    }
                }
            },
            {
                targets: [1],
                data: 'itSystemName',
                render: (data, type, row, meta) => {
                    if (type === 'display') {
                        const alreadyAssignedHTML = row.alreadyAssigned ? ` <span class="badge badge-warning">Brugeren har allerede rollen tildelt</span>` : '';
                        const hasConstraintsHTML = row.hasConstraints ? ` <span class="badge badge-warning">Kræver valg af dataafgrænsninger</span>` : '';
                        return data + alreadyAssignedHTML + hasConstraintsHTML;
                    } else {
                        return data;
                    }
                }
            },
            {
                targets: [2],
                data: 'name'
            },
            {
                targets: [3],
                data: 'description',
                orderable: false,
                className: "preformat"
            },
            {
                targets: [4],
                data: 'approver',
                orderable: false,
                searchable: false
            }
        ];
        return this.datatableService.initDefaultServersideTable(`#${tableId}`, this.buildTableUrl(), userroleColumnDefs, [[1, "asc"]]);
    }

    initRecommendedUserRoles(tableId) {
        const columns = [
            {
                targets: [0],
                data: 'id',
                orderable: false,
                searchable: false,
                render: (data, type, row, meta) => {
                    if (type === 'display') {
                        return `<input type="checkbox" class="i-checks recommendedUserRolesCheckbox userRoles${data}" value="${data}" data-name="${row.name}" data-approver="${row.approver}" data-itsystem="${row.itSystemName}" data-has-constraints="${row.hasConstraints}"/>`;
                    } else {
                        return data;
                    }
                }
            },
            {
                targets: [1],
                data: 'itSystemName'
            },
            {
                targets: [2],
                data: 'name',
                render: (data, type, row, meta) => {
                    if (type === 'display') {
                        const alreadyAssignedHTML = row.alreadyAssigned ? ` <span class="badge badge-warning">Brugeren har allerede rollen tildelt</span>` : '';
                        const hasConstraintsHTML = row.hasConstraints ? ` <span class="badge badge-warning">Kræver valg af dataafgrænsninger</span>` : '';
                        return data + alreadyAssignedHTML + hasConstraintsHTML;
                    } else {
                        return data;
                    }
                }
            },
            {
                targets: [3],
                data: 'description',
                orderable: false,
                className: "preformat"
            },
            {
                targets: [4],
                data: 'approver',
                orderable: false
            }
        ];
        return this.datatableService.initDefaultClientSideTable(`#${tableId}`, this.buildRecommendedTableUrl(), [[1, "asc"]], columns);
    }

    buildTableUrl() {
        const base = `${this.config.restUrl}/wizard/alluserroles/${this.config.userUuid}`;
        const hideAlreadyAssignedCheckbox = document.getElementById("hideAlreadyAssignedSelector");
        const hideAlreadyAssigned = hideAlreadyAssignedCheckbox.checked;
        return `${base}?hideAlreadyAssigned=${hideAlreadyAssigned}&position=${this.state.chosenEmploymentId}`;
    }

    buildRecommendedTableUrl() {
        const base = `${this.config.restUrl}/wizard/recommendeduserroles/${this.config.userUuid}`;
        const hideAlreadyAssignedCheckbox = document.getElementById("hideAlreadyAssignedSelector");
        const hideAlreadyAssigned = hideAlreadyAssignedCheckbox.checked;
        return `${base}?hideAlreadyAssigned=${hideAlreadyAssigned}&position=${this.state.chosenEmploymentId}`;
    }

    initUserRoleSelection(checkboxClass) {
        $('.' + checkboxClass).off();

        $('.' + checkboxClass).on('ifChecked', (event) => {
            const checkbox = event.target;
            const hasConstraints = checkbox.dataset.hasConstraints;
            if (hasConstraints === 'true') {
                const id = checkbox.value;
                this.constraintService.loadModal(id, checkbox);
            } else {
                this.check(checkbox);
            }
        });

        $('.' + checkboxClass).on('ifUnchecked', (event) => {
            this.uncheck(event.target);
        });
    }

    check(checkBox) {
        const id = checkBox.value;
        const name = checkBox.dataset.name;
        const itSystem = checkBox.dataset.itsystem;
        const approver = checkBox.dataset.approver;
        const type = this.config.userRoleText;

        $('.userRoles' + id).each(function () {
            if (!this.checked) {
                $(this).prop("checked", true);
            }
        });

        if (!this.state.chosenUserRoleIds.includes(id)) {
            this.state.chosenUserRoleIds.push(id);
        }

        const exists = this.state.chosenRolesDTOs.some((dto) => dto.id === id && dto.type === type);

        if (!exists) {
            this.state.chosenRolesDTOs.push({
                id,
                name,
                itSystem,
                approver,
                type
            });
        }
    }

    uncheck(checkBox) {
        const id = checkBox.value;
        const type = this.config.userRoleText;

        $('.userRoles' + id).each(function () {
            if (this.checked) {
                $(this).iCheck('uncheck');
            }
        });

        const idIndex = this.state.chosenUserRoleIds.indexOf(id);
        if (idIndex !== -1) {
            this.state.chosenUserRoleIds.splice(idIndex, 1);
        }

        this.state.chosenRolesDTOs = this.state.chosenRolesDTOs.filter((dto) => !(dto.id === id && dto.type === type));
    }
}

/**
 * Manages the "postponed constraint" modal shown when a checked role
 * requires data-scoping constraints (KLE, org unit, IT system, etc.).
 */
class ConstraintService {
    chosenConstraints = new Map();
    modalID = 'userrole-constraint-modal';
    modalContainerID = 'userrole-constraint-modal-container';
    modal = null;
    currentCheckbox = null;
    currentRoleId = null;

    constructor(config, userRoleService) {
        this.config = config;
        this.userRoleService = userRoleService;
        this.modalUrl = (roleId) => `${this.config.baseUrl}/constraintfragment?roleId=${roleId}&userUuid=${this.config.userUuid}`;
    }

    loadModal(roleId, checkboxElement) {
        this.currentCheckbox = checkboxElement;
        this.currentRoleId = roleId;

        $(`#${this.modalContainerID}`).load(this.modalUrl(roleId), () => {
            const modal = $(`#${this.modalID}`);
            this.modal = modal.modal({
                backdrop: 'static',
                keyboard: false
            });
            if (window.postponedConstraintsService) {
                window.postponedConstraintsService.initForModal(`#${this.modalID} .modal-content`);
            }
        });
    }

    addConstraints(userRoleId, constraintDTOMap) {
        this.chosenConstraints.set(userRoleId, constraintDTOMap);
        return this.chosenConstraints.get(userRoleId);
    }

    removeConstraints(userRoleId) {
        const constraintDTOMap = this.chosenConstraints.get(userRoleId);
        this.chosenConstraints.delete(userRoleId);
        return constraintDTOMap;
    }

    onModalConfirm() {
        this.userRoleService.check(this.currentCheckbox);
        this.modal.modal('hide');

        // Gather constraint values
        const constraintElements = document.querySelectorAll('.constraint');
        const constraintDTOs = [];
        for (const constraint of constraintElements) {
            const systemRoleId = constraint.dataset.systemroleid;
            const typeUuid = constraint.dataset.constrainttype;

            if (constraint.tagName === 'SELECT') {
                const checkedOptions = constraint.querySelectorAll("option:checked");
                for (const option of checkedOptions) {
                    const value = option.value;
                    if (value) {
                        constraintDTOs.push(new ConstraintDTO(systemRoleId, typeUuid, value));
                    }
                }
            } else if (constraint.tagName === 'INPUT') {
                const value = constraint.value;
                if (value) {
                    constraintDTOs.push(new ConstraintDTO(systemRoleId, typeUuid, value));
                }
            }
        }

        this.addConstraints(this.currentRoleId, constraintDTOs);

        // Reset temporary state variables
        this.currentCheckbox = null;
        this.currentRoleId = null;
    }

    onModalCancel() {
        this.userRoleService.uncheck(this.currentCheckbox);
        this.currentCheckbox = null;
        this.modal.modal('hide');
    }

    getConstraintsAsObjects() {
        const roleConstraintMappingList = [];
        for (const [userRoleId, constraintList] of this.chosenConstraints.entries()) {
            roleConstraintMappingList.push({
                userRoleId,
                roleConstraints: constraintList
            });
        }
        return roleConstraintMappingList;
    }
}

class ConstraintDTO {
    systemRoleId;
    typeUuid;
    value;

    constructor(systemRoleId, typeUuid, value) {
        this.systemRoleId = systemRoleId;
        this.typeUuid = typeUuid;
        this.value = value;
    }
}
