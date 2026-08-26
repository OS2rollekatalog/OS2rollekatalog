/**
 * Drives the removal-request wizard's steps: role selection, reason, and
 * final confirmation & submission.
 */
class RequestService {
    constructor(config, state, roleService) {
        this.config = config;
        this.state = state;
        this.roleService = roleService;
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

                // Forbid "next" if no roles have been chosen
                if (currentIndex === 0 && this.state.chosenRolesDTOs.length === 0) {
                    return false;
                }

                // Forbid "next" if no reason given and reasonSetting is OBLIGATORY
                if (currentIndex === 1 && $("#reason").val() === "" && this.config.reasonSetting === "OBLIGATORY") {
                    return false;
                }

                return true;
            },
            onStepChanged: (event, currentIndex, priorIndex) => {
                if (this.config.reasonSetting === "NONE") {
                    if (currentIndex === 1) {
                        if (priorIndex === 2) {
                            $("#requestWizard").steps("previous");
                        } else {
                            $("#requestWizard").steps("next");
                        }
                    }
                }

                if (currentIndex === 2) {
                    // Remove existing rows, if any
                    $("#confirmRolesTable tbody").empty();

                    // Add a row for each chosen role
                    this.state.chosenRolesDTOs.forEach((role) => {
                        const rowHtml = `
                            <tr>
                                <td>${role.type}</td>
                                <td>${role.itSystem}</td>
                                <td>${role.name}</td>
                            </tr>
                        `;
                        $("#confirmRolesTable tbody").append(rowHtml);
                    });

                    const reason = $("#reason").val();
                    $("#confirmReason").text(reason);
                }
            },
            onInit: (event, currentIndex) => {
                this.roleService.init();
            },
            onFinishing: (event, currentIndex) => {
                const reason = $("#reason").val();
                if (this.state.chosenRolesDTOs.length === 0 || (reason === "" && this.config.reasonSetting === "OBLIGATORY")) {
                    return false;
                }

                return true;
            },
            onFinished: (event, currentIndex) => {
                const request = {
                    userUuid: this.config.userUuid,
                    userRoles: this.state.chosenUserRoleIds,
                    roleGroups: this.state.chosenRoleGroupIds,
                    reason: $("#reason").val()
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
    }
}

/**
 * Initializes the role tables shown on the removal wizard's first step,
 * either as separate user-role / role-group tables or as a single combined
 * table, depending on config.isCombinedEnabled.
 */
class RoleService {
    constructor(config, state, roleGroupService, userRoleService, combinedService) {
        this.config = config;
        this.state = state;
        this.roleGroupService = roleGroupService;
        this.userRoleService = userRoleService;
        this.combinedService = combinedService;
    }

    init() {
        if (!this.config.isCombinedEnabled) {
            this.initRoleGroupsTable();
            this.initUserRolesTable();
        } else {
            this.initCombinedTable();
        }
    }

    initCheckboxes(checkboxClass) {
        $('.' + checkboxClass).iCheck({
            checkboxClass: 'icheckbox_square-green',
            radioClass: 'iradio_square-green'
        });
    }

    async initRoleGroupsTable() {
        const tableId = 'roleGroupTable';
        const checkboxClass = 'roleGroupsCheckbox';
        this.initCheckboxes(checkboxClass);
        const table = this.roleGroupService.initRoleGroups(tableId);
        this.roleGroupService.initRoleGroupSelection(checkboxClass);

        table.on('draw', () => {
            this.roleGroupService.initRoleGroupSelection(checkboxClass);

            $('.' + checkboxClass).each((index, element) => {
                const id = element.value;
                if (!this.state.chosenRoleGroupIds.includes(id)) {
                    $(element).iCheck('uncheck');
                } else {
                    $(element).iCheck('check');
                }
            });
        });
        table.on('click', 'td.dt-control', (e) => this.roleGroupService.openCloseDetails(table, e));
    }

	async initUserRolesTable() {
		const tableId = 'userRoleTable';
		const checkboxClass = 'userRolesCheckbox';
		this.initCheckboxes(checkboxClass);
		const table = this.userRoleService.initUserRoles(tableId);
		this.userRoleService.initUserRoleSelection(checkboxClass);

		table.on('draw', () => {
			this.userRoleService.initUserRoleSelection(checkboxClass);
		});
	}

    async initCombinedTable() {
        const tableId = 'combinedRolesTable';
        const checkboxClass = 'combinedCheckbox';

        this.combinedService.initCheckboxes(checkboxClass);
        this.combinedService.initCombinedTable(tableId, checkboxClass);
        this.combinedService.initCombinedSelection(checkboxClass);
    }
}

/**
 * Handles the role-group table (non-combined view) and role-group selection
 * state for the removal wizard.
 */
class RoleGroupService {
    constructor(config, state, datatableService, expandableRoleGroupTableService) {
        this.config = config;
        this.state = state;
        this.datatableService = datatableService;
        this.expandableRoleGroupTableService = expandableRoleGroupTableService;
    }

    initRoleGroups(tableId) {
        return $(`#${tableId}`).DataTable({
            pageLength: 25,
            responsive: true,
            autoWidth: false,
            columnDefs: [{ orderable: false, targets: [0, 1] }],
            order: [[2, "desc"]],
            language: this.datatableService.defaultLanguageOptions
        });
    }

    openCloseDetails(table, e) {
        const tr = e.target.closest('tr');
        const row = table.row(tr);
        if (row.child.isShown()) {
            row.child.hide();
        } else {
            row.child(this.formatDetails(row.data())).show();
        }
        $(tr).toggleClass('dt-hasChild');
    }

    formatDetails(rowData) {
        const id = rowData[0];
        const url = `${this.config.detailsUrl}/${id}/userroles`;

        const div = $('<div/>')
            .addClass('loading')
            .text('Henter...');

        $.ajax({
            url,
            success: (data) => {
                div.html(data).removeClass('loading');
                this.expandableRoleGroupTableService.initUserRoleTable();
            }
        });

        return div;
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
        const type = this.config.roleGroupText;

        if (!this.state.chosenRoleGroupIds.includes(id)) {
            this.state.chosenRoleGroupIds.push(id);
        }

        const exists = this.state.chosenRolesDTOs.some((dto) => dto.id === id && dto.type === type);

        if (!exists) {
            this.state.chosenRolesDTOs.push({
                id,
                name,
                itSystem: "",
                type
            });
        }
    }

    uncheck(checkbox) {
        const id = checkbox.value;
        const type = this.config.roleGroupText;

        const idIndex = this.state.chosenRoleGroupIds.indexOf(id);
        if (idIndex !== -1) {
            this.state.chosenRoleGroupIds.splice(idIndex, 1);
        }

        this.state.chosenRolesDTOs = this.state.chosenRolesDTOs.filter((dto) => !(dto.id === id && dto.type === type));
    }
}

/**
 * Handles the combined (user roles + role groups in one table) view for the
 * removal wizard, used when config.isCombinedEnabled is true.
 */
class CombinedRoleService {
    constructor(config, state, datatableService, roleGroupService, userRoleService, expandableRoleGroupTableService) {
        this.config = config;
        this.state = state;
        this.datatableService = datatableService;
        this.roleGroupService = roleGroupService;
        this.userRoleService = userRoleService;
        this.expandableRoleGroupTableService = expandableRoleGroupTableService;
    }

    initCombinedSelection(checkboxClass) {
        $('.' + checkboxClass).off('ifChecked ifUnchecked');

        $('.' + checkboxClass).on('ifChecked', (event) => {
            const checkbox = event.target;
            const type = $(checkbox).data('type');
            const hasConstraints = $(checkbox).data('has-constraints');

            if (type === 'userRole' && hasConstraints === 'true') {
                const id = checkbox.value;
                // NOTE: pre-existing bug, kept unchanged - see notes below.
                window.constraintService.loadModal(id, checkbox);
            } else if (type === 'userRole') {
                this.userRoleService.check(checkbox);
            } else if (type === 'roleGroup') {
                this.roleGroupService.check(checkbox);
            }
        });

        $('.' + checkboxClass).on('ifUnchecked', (event) => {
            const checkbox = event.target;
            const type = $(checkbox).data('type');

            if (type === 'userRole') {
                this.userRoleService.uncheck(checkbox);
            } else if (type === 'roleGroup') {
                this.roleGroupService.uncheck(checkbox);
            }
        });
    }

    initCombinedTable(tableId, checkboxClass) {
        const table = $(`#${tableId}`).DataTable({
            pageLength: 25,
            responsive: true,
            autoWidth: false,
            columnDefs: [
                { orderable: false, targets: [0, 1] },
                { searchable: false, targets: [0, 1] }
            ],
            order: [
                [2, "asc"],
                [4, "asc"]
            ],
            language: this.datatableService.defaultLanguageOptions
        });

        table.on('draw', () => {
            this.initCombinedSelection(checkboxClass);

            $('.' + checkboxClass).each((index, element) => {
                const id = element.value;
                const type = $(element).data('type');

                let isChecked = false;
                if (type === 'userRole') {
                    isChecked = this.state.chosenUserRoleIds.includes(id);
                } else if (type === 'roleGroup') {
                    isChecked = this.state.chosenRoleGroupIds.includes(id);
                }

                if (isChecked) {
                    $(element).iCheck('check');
                } else {
                    $(element).iCheck('uncheck');
                }
            });
        });

        table.on('click', 'td.dt-control', (e) => {
            const tr = $(e.target).closest('tr');
            const row = table.row(tr);
            const roleGroupId = $(e.target).data('rolegroup-id');

            if (row.child.isShown()) {
                row.child.hide();
                $(tr).removeClass('dt-hasChild');
            } else {
                const url = `${this.config.detailsUrl}/${roleGroupId}/userroles`;

                const div = $('<div/>')
                    .addClass('loading')
                    .text('Henter...');

                row.child(div).show();
                $(tr).addClass('dt-hasChild');

                $.ajax({
                    url,
                    success: (data) => {
                        div.html(data).removeClass('loading');
                        this.expandableRoleGroupTableService.initUserRoleTable();
                    }
                });
            }
        });
    }

    initCheckboxes(checkboxClass) {
        $('.' + checkboxClass).iCheck({
            checkboxClass: 'icheckbox_square-green',
            radioClass: 'iradio_square-green'
        });
    }
}

/**
 * Handles the user-role table (non-combined view) and user-role selection
 * state for the removal wizard.
 */
class UserRoleService {
    constructor(config, state, datatableService) {
        this.config = config;
        this.state = state;
        this.datatableService = datatableService;
    }

    initUserRoles(tableId) {
        return $(`#${tableId}`).DataTable({
            pageLength: 25,
            responsive: true,
            autoWidth: false,
            order: [[0, "desc"]],
            language: this.datatableService.defaultLanguageOptions
        });
    }

    initUserRoleSelection(checkboxClass) {
        $('.' + checkboxClass).off();

        $('.' + checkboxClass).on('ifChecked', (event) => {
            const checkbox = event.target;
            const hasConstraints = checkbox.dataset.hasConstraints;
            if (hasConstraints === 'true') {
                const id = checkbox.value;
                // NOTE: pre-existing bug, kept unchanged - see notes below.
                window.constraintService.loadModal(id, checkbox);
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
        const type = this.config.userRoleText;

        if (!this.state.chosenUserRoleIds.includes(id)) {
            this.state.chosenUserRoleIds.push(id);
        }

        const exists = this.state.chosenRolesDTOs.some((dto) => dto.id === id && dto.type === type);

        if (!exists) {
            this.state.chosenRolesDTOs.push({
                id,
                name,
                itSystem,
                type
            });
        }
    }

    uncheck(checkBox) {
        const id = checkBox.value;
        const type = this.config.userRoleText;

        const idIndex = this.state.chosenUserRoleIds.indexOf(id);
        if (idIndex !== -1) {
            this.state.chosenUserRoleIds.splice(idIndex, 1);
        }

        this.state.chosenRolesDTOs = this.state.chosenRolesDTOs.filter((dto) => !(dto.id === id && dto.type === type));
    }
}
