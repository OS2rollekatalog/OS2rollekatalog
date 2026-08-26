// Edit page for a role group: inline name/description edits, the useronly
// and OU-filter flags, requester/approver permission selects, and the
// roles/users/OUs tabs.

let pageConfig;
let userService;
let orgUnitService;
let ouFilterService;
let requestApproveService;
let userroleTableService;

document.addEventListener("DOMContentLoaded", () => {
    pageConfig = JSON.parse(document.getElementById("rolegroup-edit-config").textContent);
    window.token = $("meta[name='_csrf']").attr("content");

    userService = new UserService(pageConfig);
    window.userService = userService;
    // Alias expected by the shared user_role_group_modal script
    window.rolesService = userService;

    orgUnitService = new OrgUnitService(pageConfig);
    window.orgUnitService = orgUnitService;

    requestApproveService = new RequestApproveService();
    ouFilterService = new OUFilterService(pageConfig);
    userroleTableService = new UserroleTableService(pageConfig);

    userroleTableService.initUserroleTable(pageConfig.roleId);

    userService.loadRolesFragment();
    // Edit role-group assignment from the user modal
    window.userRoleEditModalService.parentService = userService;
    window.bulkAssignRoleModalService.parentService = userService;

    orgUnitService.loadRolesFragment();
    // Edit role-group assignment from the OU modal
    window.ouRoleAssignmentService.parentService = orgUnitService;

    $("#name").on("change", handleNameOrDescriptionChange);
    $("#description").on("change", handleNameOrDescriptionChange);

    $(".useronly-checkbox").on("change", handleUserOnlyFlagChange);
    $(".useronly-checkbox").on("change", () => orgUnitService.loadRolesFragment());

    $("#requesterSettingsSelect").on("change", handleRoleGroupRequesterPermissionChange);
    $("#approverSettingsSelect").on("change", handleRoleGroupApproverPermissionChange);

    window.select2Service = window.select2Service || new Select2Service();
    window.select2Service.initSelect("#requesterSettingsSelect, #approverSettingsSelect", {
        placeholder: "",
        allowClear: true,
        multiple: true
    });

    window.sweetAlertService = window.sweetAlertService || new SweetAlertService();

    new TextareaAutosizeService().init("#description");

    initCheckboxDelegation();
    initFragmentDelegation();

    ouFilterService.init();
});

/**
 * Posts the role group's name/description to the server on change.
 */
function handleNameOrDescriptionChange() {
    const roleGroup = {
        id: $("#id").val(),
        name: $("#name").val(),
        description: $("#description").val()
    };

    $.ajax({
        url: pageConfig.url + "edit",
        method: "POST",
        headers: {
            "X-CSRF-TOKEN": window.token
        },
        data: roleGroup,
        error: errorHandler(pageConfig.fieldNotUpdatedMsg),
        success: () => {
            window.notificationService.showInfoNotification(pageConfig.fieldUpdatedMsg);
        }
    });
}

function handleUserOnlyFlagChange() {
    const flag = String(this.dataset.flag);
    const active = this.checked;
    const updateUrl = `${pageConfig.url}flag/${pageConfig.roleId}/${flag}?active=${active}`;

    $.ajax({
        url: updateUrl,
        method: "POST",
        headers: {
            "X-CSRF-TOKEN": window.token
        },
        error: defaultErrorHandler,
        success: () => {
            window.notificationService.showInfoNotification(pageConfig.fieldUpdatedMsg);
        }
    });
}

function handleCheckboxChange() {
    const objType = String(this.dataset.objtype);
    const action = this.checked ? "add" : "remove";
    const ajaxOptions = getRoleAssignmentAjaxObject(
        `${pageConfig.url}${action}${objType}/${this.value}/${this.id}`,
        pageConfig.fieldUpdatedMsg,
        pageConfig.fieldNotUpdatedMsg
    );

    $.ajax(ajaxOptions);

    $(`#${this.id}sortLabel`).text(this.checked);
}

function getRoleAssignmentAjaxObject(requestUrl, okMsg, errorMsg) {
    return {
        url: requestUrl,
        method: "POST",
        headers: {
            "X-CSRF-TOKEN": window.token
        },
        error: errorHandler(errorMsg),
        success: () => {
            window.notificationService.showInfoNotification(okMsg);
        }
    };
}

async function handleRoleGroupRequesterPermissionChange() {
    const groupId = String(this.dataset.groupid);
    const selectedValues = Array.from(this.selectedOptions).map((option) => option.value);
    requestApproveService.handleRequesterRestrictions();

    const response = await fetch(`${pageConfig.url}${groupId}/requester`, {
        method: "POST",
        headers: {
            "X-CSRF-TOKEN": window.token,
            "Content-Type": "application/json"
        },
        body: JSON.stringify({ requesterPermission: selectedValues })
    });

    if (!response.ok) {
        defaultErrorHandler(response);
        return;
    }

    $.notify({ message: pageConfig.fieldUpdatedMsg }, { status: "success", autoHideDelay: 4000 });
}

async function handleRoleGroupApproverPermissionChange() {
    const groupId = String(this.dataset.groupid);
    const selectedValues = Array.from(this.selectedOptions).map((option) => option.value);
    requestApproveService.handleApproverRestrictions();

    const response = await fetch(`${pageConfig.url}${groupId}/approver`, {
        method: "POST",
        headers: {
            "X-CSRF-TOKEN": window.token,
            "Content-Type": "application/json"
        },
        body: JSON.stringify({ approverPermission: selectedValues })
    });

    if (!response.ok) {
        defaultErrorHandler(response);
        return;
    }

    $.notify({ message: pageConfig.fieldUpdatedMsg }, { status: "success", autoHideDelay: 4000 });
}

/**
 * Delegated change listener for the role-assignment checkboxes in the roles
 * table. Delegated on tbody since DataTables regenerates rows on each draw.
 */
function initCheckboxDelegation() {
    $("#listTable tbody").on("change", ".checkboxaction", handleCheckboxChange);
}

/**
 * Delegated click listeners for links/buttons rendered inside the
 * ajax-loaded users/ous tab fragments. Delegated on the stable tab
 * containers, since their inner content is replaced via $.load().
 *
 * NOTE: the original inline onclick handlers never called preventDefault(),
 * so clicking these href="#" links scrolls to the top of the page. That
 * quirk is preserved here intentionally.
 */
function initFragmentDelegation() {
    $("#users_menu").on("click", ".js-add-user-role", () => {
        userService.loadAddUserRoleFragment();
    });

    $("#users_menu").on("click", ".js-add-user-role-multiple", () => {
        window.bulkAssignRoleModalService.start(pageConfig.roleId, pageConfig.roleName, "rolegroup");
    });

    $("#users_menu").on("click", ".js-back-to-user-roles", () => {
        userService.loadRolesFragment();
    });

    $("#users_menu").on("click", ".js-edit-user-assignment", function () {
        userService.editRoleAssignment(this);
    });

    $("#users_menu").on("click", ".js-delete-user-assignment", function () {
        userService.deleteRoleAssignment(this);
    });

    $("#users_menu").on("click", ".js-add-role-group", function () {
        userService.addRoleGroup(this.dataset.uuid);
    });

    $("#ous_menu").on("click", ".js-add-ou-role", () => {
        orgUnitService.loadAddRoleGroupFragment();
    });

    $("#ous_menu").on("click", ".js-back-to-ou-roles", () => {
        orgUnitService.loadRolesFragment();
    });

    $("#ous_menu").on("click", ".js-add-ou-role-assignment", function () {
        orgUnitService.addRoleAssignment(this);
    });

    $("#ous_menu").on("click", ".js-edit-ou-assignment", function () {
        orgUnitService.editRoleAssignment(this);
    });

    $("#ous_menu").on("click", ".js-delete-ou-assignment", function () {
        orgUnitService.deleteRoleAssignment(this);
    });
}

/**
 * Manages the "assigned users" / "available users" tab for a role group.
 */
class UserService {
    constructor(config) {
        this.config = config;
    }

    loadRolesFragment() {
        new QueueDrainService("#users_menu", { message: this.config.txtQueueSpinner }).watch(() => {
            $("#users_menu").load(`${this.config.uiUrl}${this.config.roleId}/assignedUsersFragment?showEdit=true`, () => {
                fragShowDataTableFun("#listTableUsers", 0);
            });
        });
    }

    // Alias used by the shared user_role_group_modal after a role group is added.
    loadRolesFragmentWhenReady() {
        this.loadRolesFragment();
    }

    // user_role_group_modal calls this after assigning a role group directly.
    loadAddRoleGroupFragment() {
        this.loadAddUserRoleFragment();
    }

    // Alias used by the shared user_role_group_modal after a role group is added.
    loadAddRoleGroupFragmentWhenReady() {
        this.loadAddRoleGroupFragment();
    }

    loadAddUserRoleFragment() {
        $("#users_menu").load(`${this.config.uiUrl}${this.config.roleId}/availableUsersFragment`, () => {
            const columnDefOptions = [
                {
                    targets: [0],
                    data: "name",
                    render: (data, type, row) => {
                        if (type !== "display") {
                            return data;
                        }

                        let html = "<div><div>";
                        html += `<span>${data}</span>`;
                        html += row.disabled ? '<span class="badge badge-warning">Deaktiveret</span>' : "";
                        html += "</div>";
                        html += row.isAlreadyAssigned ? '<div style="font-size: smaller; color: red;">Tildelt</div>' : "";
                        html += "</div>";
                        return html;
                    }
                },
                {
                    targets: [1],
                    data: "userId"
                },
                {
                    targets: [2],
                    data: "positions",
                    render: (data) => {
                        let html = '<ul style="list-style: none;">';
                        for (const position of data) {
                            html += `<li>${position.name} i ${position.orgUnit.name}</li>`;
                        }
                        html += "</ul>";
                        return html;
                    }
                },
                {
                    targets: [3],
                    data: "uuid",
                    orderable: false,
                    searchable: false,
                    sortable: false,
                    render: (data) => (
                        data ? `<a href="#" class="js-add-role-group" data-uuid="${data}"><em class="fa fa-plus"></em></a>` : null
                    )
                }
            ];

            const table = new DatatableService().initDefaultServersideTable(
                "#listTableUsersAdd",
                `${this.config.userRestUrl}available/rolegroup/${this.config.roleId}`,
                columnDefOptions
            );

            // Focus the search field
            const searchInput = table.table().container().querySelector(".dataTables_filter input");
            searchInput.focus();
        });
    }

    deleteRoleAssignment(elem) {
        const parent = elem.parentElement;
        const assignmentId = parent.dataset.assignmentid;
        const user = parent.dataset.user;
        const roleName = parent.dataset.name;

        const endpoint = `${this.config.userRestUrl}${user}/removeassignment/ROLEGROUP/DIRECT/${assignmentId}`;
        const titleTxt = `${this.config.deleteRoleAssignmentTitleTxt}"${roleName}"`;

        window.sweetAlertService.confirm(
            titleTxt,
            this.config.deleteRoleAssignmentBodyTxt,
            this.config.deleteAssignmentConfirmTxt,
            this.config.deleteAssignmentCancelTxt,
            () => {
                $.ajax({
                    url: endpoint,
                    method: "POST",
                    headers: {
                        "X-CSRF-TOKEN": window.token
                    },
                    error: errorHandler(this.config.deleteRoleAssignmentErrorMessage),
                    success: () => {
                        this.loadRolesFragment();
                        window.notificationService.showInfoNotification(this.config.deleteRoleAssignmentSuccessMessage);
                    }
                });
            }
        );
    }

    editRoleAssignment(elem) {
        const parent = elem.parentElement;
        const assignmentId = parent.dataset.assignmentid;
        const startDate = parent.dataset.startdate;
        const stopDate = parent.dataset.stopdate;
        const user = parent.dataset.user;
        const orgUnit = parent.dataset.ouuuid;
        const caseNumber = parent.dataset.casenumber;

        $.ajax({
            method: "GET",
            url: `${this.config.userRestUrl}${user}/orgunits`,
            success: (response) => {
                window.userRoleEditModalService.showModal(
                    user, startDate, stopDate, "ROLEGROUP", assignmentId, "DIRECT", orgUnit, response, caseNumber, this.config.roleName
                );
            }
        });
    }

    addRoleGroup(uuid) {
        $("#userRoleGroupModal").load(`${this.config.uiUrl}fragments/${uuid}`, () => {
            const modalGroups = $("#modal-groups");

            window.initGroupModalDatePickers();
            window.roleGroupModalService.init()
            window.roleGroupModalService.parentService = this;

            modalGroups.modal({ backdrop: "static", keyboard: false });

            $("#groupStopDatePicker").data("DateTimePicker").clear();
            $("#groupStartDatePicker").data("DateTimePicker").clear();
            $("#groupStartDatePicker").data("DateTimePicker").date(new Date());
            $("#assignRoleGroupName").text(this.config.roleName);

            modalGroups.attr("rolegroupid", this.config.roleId);
            modalGroups.attr("userUuid", uuid);
        });
    }
}

/**
 * Manages the "assigned OUs" / "available OUs" tab for a role group.
 */
class OrgUnitService {
    constructor(config) {
        this.config = config;
    }

    loadRolesFragment() {
        $("#ous_menu").load(`${this.config.uiUrl}${this.config.roleId}/assignedOrgUnitsFragment?showEdit=true`, () => {
            fragShowDataTableFun("#listTableOus", 0);
        });
    }

    loadAddRoleGroupFragment() {
        $("#ous_menu").load(`${this.config.uiUrl}${this.config.roleId}/availableOrgUnitsFragment`, () => {
            fragShowDataTableFun("#listTableOusAdd", 0);
        });
    }

    deleteRoleAssignment(elem) {
        const parent = elem.parentElement;
        const assignmentId = parent.dataset.assignmentid;
        const ou = parent.dataset.orgunit;
        const roleName = parent.dataset.name;

        const endpoint = `${this.config.ouRestUrl}${ou}/removeassignment/ROLEGROUP/${assignmentId}`;
        const titleTxt = `${this.config.deleteRoleAssignmentTitleTxt}"${roleName}"`;

        window.sweetAlertService.confirm(
            titleTxt,
            this.config.deleteRoleAssignmentBodyTxt,
            this.config.deleteAssignmentConfirmTxt,
            this.config.deleteAssignmentCancelTxt,
            () => {
                $.ajax({
                    url: endpoint,
                    method: "POST",
                    headers: {
                        "X-CSRF-TOKEN": window.token
                    },
                    error: errorHandler(this.config.deleteRoleAssignmentErrorMessage),
                    success: () => {
                        this.loadRolesFragment();
                        window.notificationService.showInfoNotification(this.config.deleteRoleAssignmentSuccessMessage);
                    }
                });
            }
        );
    }

    editRoleAssignment(elem) {
        const parent = elem.parentElement;
        const startDate = parent.dataset.startdate;
        const stopDate = parent.dataset.stopdate;
        const assignmentType = parent.dataset.assignmenttype;
        const assignmentId = parent.dataset.assignmentid;
        const ou = parent.dataset.orgunit;

        $("#ouRoleGroupEditModal").load(`${this.config.uiUrl}fragments/ou/${ou}?edit=true`, () => {
            window.initOuEditModalDatePickers();
            window.ouRoleAssignmentService.init();

            window.ouRolesEditModalService.assignmentId = assignmentId;
            window.ouRolesEditModalService.ouUuid = ou;
            window.ouRolesEditModalService.roleType = "rolegroup";
            window.ouRolesEditModalService.roleId = this.config.roleId;
            window.ouRolesEditModalService.inherit = Number(assignmentType) === -2;

            window.ouRolesEditModalService.showAssignModal(
                this.config.titlesEnabled, startDate, stopDate, assignmentType, assignmentId, this.config.roleName
            );
        });
    }

    addRoleAssignment(elem) {
        window.ouRolesModalService.roleType = "rolegroup";
        window.ouRolesModalService.roleId = this.config.roleId;
        window.ouRolesModalService.ouUuid = elem.dataset.orgunit;

        $("#ouRoleGroupModal").load(`${this.config.uiUrl}fragments/ou/${window.ouRolesModalService.ouUuid}`, () => {
            window.initOuModalDatePickers();
            window.ouRoleAssignmentService.init();
            window.ouRoleAssignmentService.parentService = this;

            window.ouRolesModalService.showAssignModal(this.config.titlesEnabled, null, null, this.config.roleName);
        });
    }
}

/**
 * Manages the OU-filter checkbox, modal, and jsTree selection for a role group.
 */
class OUFilterService {
    constructor(config) {
        this.config = config;
        window.jsTreeService = window.jsTreeService || new JsTreeService();
        this.jsTreeService = window.jsTreeService;
    }

    init() {
        $("#ouFilterEnabled-checkbox").on("change", () => this.handleOUFilterCheckbox());
        $("#ouFilterBtn").on("click", () => this.handleOUFilterBtnClick());
        $("#oufilterSave").on("click", () => this.saveOUs());

        $("#modal-ou-filter").on("shown.bs.modal", () => {
            $("#ou-filter-tree-search").focus();
        });

        this.toggleOUFilter();
        this.initTree();
    }

    initTree() {
        this.jsTreeService.initTree("#ou-filter-tree", {
            core: {
                data: this.config.treeOUs
            },
            search: {
                show_only_matches: true,
                search_callback: (str, node) => node.text.toUpperCase().startsWith(str.toUpperCase())
            },
            checkbox: {
                keep_selected_style: false,
                three_state: false,
                cascade: "undetermined"
            },
            plugins: ["checkbox", "search"]
        });

        // Deactivate nodes except for possibleFilterOus and their descendants
        $("#ou-filter-tree").on("ready.jstree", () => {
            const treeInstance = this.jsTreeService.getInstance("#ou-filter-tree");

            treeInstance.get_json("#", { flat: true }).forEach((node) => {
                if (this.config.possibleFilterOus != null && !this.config.possibleFilterOus.includes(node.id)) {
                    treeInstance.disable_node(node.id);
                }
            });

            treeInstance.select_node(this.config.selectedFilterOUs);
        });

        // Searching in the JSTree
        let searchTimeout = false;
        $("#ou-filter-tree-search").on("keyup", () => {
            if (searchTimeout) {
                clearTimeout(searchTimeout);
            }

            searchTimeout = setTimeout(() => {
                const searchValue = $("#ou-filter-tree-search").val();
                this.jsTreeService.getInstance("#ou-filter-tree").search(searchValue);
            }, 400);
        });
    }

    handleOUFilterBtnClick() {
        $("#modal-ou-filter").modal("show");
    }

    handleOUFilterCheckbox() {
        const checked = $("#ouFilterEnabled-checkbox").prop("checked");

        $.ajax({
            url: "/rest/rolegroups/ouFilterEnabled",
            method: "POST",
            data: {
                ouFilterEnabled: checked,
                id: this.config.roleId
            },
            headers: {
                "X-CSRF-TOKEN": window.token
            },
            error: errorHandler(this.config.fieldNotUpdatedMsgOUFilter),
            success: () => {
                $.notify({ message: this.config.fieldUpdatedMsg }, { status: "success", autoHideDelay: 2000 });
            }
        });

        this.toggleOUFilter();
    }

    toggleOUFilter() {
        const checked = $("#ouFilterEnabled-checkbox").prop("checked");

        if (checked) {
            $("#ouFilterButtonField").show();
        } else {
            $("#ouFilterButtonField").hide();
        }
    }

    saveOUs() {
        const selected = this.jsTreeService.getInstance("#ou-filter-tree").get_selected();

        $.ajax({
            url: `${this.config.ouFilterRestUrl}oufilter`,
            method: "POST",
            headers: {
                "X-CSRF-TOKEN": window.token,
                "Content-Type": "application/json"
            },
            data: JSON.stringify({
                id: this.config.roleId,
                selectedOUs: selected
            }),
            error: defaultErrorHandler,
            success: () => {
                $("#modal-ou-filter").modal("hide");
                location.reload();
            }
        });
    }
}

/**
 * Manages the "assign roles to this role group" DataTable and its
 * checkbox column.
 */
class UserroleTableService {
    constructor(config) {
        this.config = config;
    }

    initUserroleTable(rolegroupId) {
        const columnDefOptions = [
            {
                targets: [0],
                data: "selected",
                orderable: true,
                searchable: false,
                render: (data, type, row) => {
                    if (type !== "display") {
                        return data;
                    }

                    return "<div class=\"checkbox c-checkbox\"> " +
                        "<label>" +
                        `<input class="checkboxaction" id="${row.compositeKey.id}" type="checkbox" ` +
                        `value="${rolegroupId}" ${row.selected ? "checked " : ""}${row.readOnly ? "disabled " : ""}` +
                        "data-objtype=\"role\"/> " +
                        "<span class=\"fa fa-check\"></span> " +
                        "</label>" +
                        "</div>";
                }
            },
            {
                targets: [1],
                data: "name"
            },
            {
                targets: [2],
                data: "itSystemName"
            },
            {
                targets: [3],
                data: "description",
                className: "preformat"
            }
        ];

        return new DatatableService().initDefaultServersideTable(
            "#listTable",
            `${this.config.url}${rolegroupId}/userroles`,
            columnDefOptions
        );
    }
}
