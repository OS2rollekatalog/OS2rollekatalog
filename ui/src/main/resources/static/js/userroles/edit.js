// Edit page for a user role: inline field edits, system-role/rolegroup
// checkboxes, requester/approver permissions, OU filter, manager-action
// settings, and the users/OUs assignment tabs.
let pageConfig;
let userService;
let orgUnitService;
let managerActionService;
let ouFilterService;
let requestApproveService;
let rolesTable;
let groupsTable;

document.addEventListener("DOMContentLoaded", () => {
    pageConfig = JSON.parse(document.getElementById("userrole-edit-config").textContent);
    window.token = $("meta[name='_csrf']").attr("content");

    userService = new UserService(pageConfig);
    window.userService = userService;

    orgUnitService = new OrgUnitService(pageConfig);
    window.orgUnitService = orgUnitService;

    managerActionService = new ManagerActionService(pageConfig);
    ouFilterService = new OUFilterService(pageConfig);
    requestApproveService = new RequestApproveService();

    window.notificationService = window.notificationService || new NotificationService();
    window.sweetAlertService = window.sweetAlertService || new SweetAlertService();
    window.jsTreeService = window.jsTreeService || new JsTreeService();
    window.select2Service = window.select2Service || new Select2Service();

    new TextareaAutosizeService().init("#description");

    // Must run before showSelectedRoles() below, since it depends on
    // functionConstraintService / orgUnitConstraintService existing.
    window.initConstraintEditor();

    userService.loadRolesFragment();
    window.userRoleModalService.parentService = userService;
    window.userRoleEditModalService.parentService = userService;
    window.bulkAssignRoleModalService.parentService = userService;

    orgUnitService.loadRolesFragment();
    window.ouRoleAssignmentService.parentService = orgUnitService;

    $("#name").on("change", handleChangeOnInput);
    $("#contactEmail").on("change", handleChangeOnInput);
    $("#advisEmail").on("change", handleChangeOnInput);
    $("#description").on("change", handleChangeOnInput);
    $("#emailTemplateTitle").on("change", handleChangeOnInput);
    $("#emailTemplateMessage").on("summernote.blur", handleChangeOnInput);

    rolesTable = initRolesTable();
	groupsTable = initGroupsTable();

	$(".useronly-checkbox").on("change", flagCheckboxChange);
    $(".useronly-checkbox").on("change", () => orgUnitService.loadRolesFragment());
    $(".inherit-checkbox").on("change", flagCheckboxChange);
    $(".sensitive-checkbox").on("change", flagCheckboxChange);
    $(".extra-sensitive-checkbox").on("change", flagCheckboxChange);
    $(".by-attestation-responsible-checkbox").on("change", flagCheckboxChange);
    $("#requesterSettingsSelect").on("change", handleUserRoleRequesterPermissionChange);
    $("#approverSettingsSelect").on("change", handleUserRoleApproverPermissionChange);

    // Reassign checkbox listeners whenever the DataTable redraws (paging/sorting).
    $("#listTable").on("draw.dt", () => {
		addCheckboxListeners(rolesTable);
		showSelectedRoles(rolesTable);
	});
	$("#listTable3").on("draw.dt", () => {
		addGroupsCheckboxListeners(groupsTable);
	});

	addCheckboxListeners(rolesTable);
	addGroupsCheckboxListeners(groupsTable);
	showSelectedRoles(rolesTable);

    $("[data-toggle='popover']").popover();
    window.select2Service.initSelect("#requesterSettingsSelect, #approverSettingsSelect", {
        placeholder: "",
        allowClear: true,
        multiple: true
    });

    $("#emailTemplateMessage").summernote({
        height: 320,
        toolbar: [
            ["font", ["bold", "italic", "underline"]],
            ["para", ["ul", "ol"]],
            ["insert", ["picture", "link"]]
        ],
        maximumImageFileSize: 100 * 1024, // 100 KB
        callbacks: {
            onImageUploadError: () => {
                window.sweetAlertService.confirm(
                    pageConfig.swalImageTitle,
                    pageConfig.swalImageText,
                    pageConfig.swalImageOk,
                    null,
                    () => {},
                    { customConfig: { showCancelButton: false, confirmButtonColor: "#4765a0" } }
                );
            }
        },
        dialogsInBody: true,
        onChange: handleChangeOnInput
    });

    $(".js-remove-link-to-system-role").on("click", removeLinkToSystemRole);

    initFragmentDelegation();

    managerActionService.init();
    ouFilterService.init();

    // NOTE: orgUnitConstraintService.init() call deferred to part 2.
});

/**
 * Posts a changed top-level field (name, description, emails, email
 * template title/message) to the server.
 */
function handleChangeOnInput() {
    const userRole = {
        id: $("#id").val(),
        name: $("#name").val(),
        contactEmail: $("#contactEmail").val(),
        advisEmail: $("#advisEmail").val(),
        description: $("#description").val(),
        itSystem: $("#itSystem").val(),
        emailTemplateTitle: $("#emailTemplateTitle").val(),
        emailTemplateMessage: $("#emailTemplateMessage").val()
    };

    $.ajax({
        url: `${pageConfig.url}edit`,
        method: "POST",
        data: userRole,
        headers: {
            "X-CSRF-TOKEN": window.token
        },
        error: errorHandler(pageConfig.fieldNotUpdatedMsg),
        success: () => {
            window.notificationService.showInfoNotification(pageConfig.fieldUpdatedMsg);
        }
    });
}

function flagCheckboxChange() {
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

function removeLinkToSystemRole() {
    const endpoint = `${pageConfig.url}removeSystemRoleLink/${pageConfig.roleId}`;

    window.sweetAlertService.confirm(
        "",
        pageConfig.unlinkFromSystemRoleBodyTxt,
        pageConfig.yesBtnTxt,
        pageConfig.cancelBtnTxt,
        () => {
            $.ajax({
                url: endpoint,
                method: "POST",
                headers: {
                    "X-CSRF-TOKEN": window.token
                },
                error: errorHandler(pageConfig.unlinkFromSystemRoleErrorMessage),
                success: () => {
                    location.reload();
                }
            });
        }
    );
}

// DataTables plug-in for sorting checkboxes.
// from: https://datatables.net/plug-ins/sorting/custom-data-source/dom-checkbox
$.fn.dataTable.ext.order["dom-checkbox"] = function (settings, col) {
    return this.api().column(col, { order: "index" }).nodes().map((td) => (
        $("input", td).prop("checked") ? "1" : "0"
    ));
};

function initRolesTable() {
    return $("#listTable").DataTable({
        columns: [
            { orderDataType: "dom-checkbox", className: "details-control" },
            {},
            {}
        ],
        order: [[1, "asc"]],
        autoWidth: false,
        stateSave: true,
        pageLength: 100,
        language: {
            search: pageConfig.searchTxt,
            lengthMenu: pageConfig.dropdownTxt,
            info: pageConfig.infoDefaultTxt,
            zeroRecords: pageConfig.infoEmptyTxt,
            infoEmpty: "",
            infoFiltered: pageConfig.infoFilteredTxt
        }
    });
}

function initGroupsTable() {
    return $("#listTable3").DataTable({
        columns: [
            { orderDataType: "dom-checkbox", className: "details-control" },
            {},
            {}
        ],
        order: [[1, "asc"]],
        autoWidth: false,
        stateSave: true,
        pageLength: 100,
        language: {
            search: pageConfig.searchTxt,
            lengthMenu: pageConfig.dropdownTxt,
            info: pageConfig.infoDefaultTxt,
            zeroRecords: pageConfig.infoEmptyTxt,
            infoEmpty: "",
            infoFiltered: pageConfig.infoFilteredTxt
        }
    });
}

/**
 * Expands the constraint-editor row for each already-checked system role,
 * and pre-fills any freetext/KLE/OU/function constraint inputs.
 *
 * NOTE: showOrHideFreetextAndFetchValue / enableFreetextAndFetchValue are
 * defined in part 2 of this file (the constraint editor).
 */
function showSelectedRoles(table) {
    $(".role-checkbox[data-roleid]:checked").each(function () {
        const tr = $(this).closest("tr");
        const id = this.dataset.roleid;
        const row = table.row(tr);
        const template = $(`#constraints-${id}:not(:has(#no-constraints))`).first();
        const child = template.clone().show();
        row.child(child).show();

        showOrHideFreetextAndFetchValue(id, pageConfig.kleConstraintUuid);
        showOrHideFreetextAndFetchValue(id, pageConfig.ouConstraintUuid);
        showOrHideFreetextAndFetchValue(id, pageConfig.internalOuConstraintUuid);
        enableFreetextAndFetchValue(id, pageConfig.itSystemConstraintUuid, null);

        const constraints = pageConfig.systemRoleComboMultiConstraintUuids[id];
        if (constraints) {
            constraints.forEach((uuid) => {
                enableFreetextAndFetchValue(id, uuid, "COMBO_MULTI");
            });
        }
    });
}

function addCheckboxListeners(table) {
    $(".role-checkbox").off("change");

    $(".role-checkbox").on("change", function () {
        const input = $(this);
        const tr = $(this).closest("tr");
        const id = this.dataset.roleid;
        const row = table.row(tr);
        const template = $(`#constraints-${id}`).first();

        if (!this.checked) {
            removeSystemRole(id, () => {
                template.find("input.constraint-checkbox").prop("checked", false);
                template.find("select").prop("disabled", true);
                row.child.hide();
            }, () => {
                input[0].checked = true; // keep checkbox selected on failure
            });
        } else {
            addSystemRole(id, () => {
                if (template.has("#no-constraints").length === 0) {
                    const child = template.clone().show();
                    row.child(child).show();
                    $("[data-toggle='popover']").popover();
                }
            }, () => {
                input[0].checked = false; // keep checkbox unselected on failure
            });
        }
    });
}

function addSystemRole(id, onSuccess, onError) {
    const roleId = $("[name='id']").val();
    const newUrl = `${pageConfig.url}edit/${roleId}/addSystemRole/${id}`;

    $.ajax({
        url: newUrl,
        method: "POST",
        headers: {
            "X-CSRF-TOKEN": window.token
        },
        error: (response) => {
            onError();
            errorHandler(pageConfig.fieldNotUpdatedMsg)(response);
        },
        success: () => {
            onSuccess();
            window.notificationService.showInfoNotification(pageConfig.fieldUpdatedMsg);
        }
    });
}

function removeSystemRole(id, onSuccess, onError) {
    const roleId = $("[name='id']").val();
    const newUrl = `${pageConfig.url}edit/${roleId}/removeSystemRole/${id}`;

    $.ajax({
        url: newUrl,
        method: "POST",
        headers: {
            "X-CSRF-TOKEN": window.token
        },
        error: (response) => {
            onError();
            errorHandler(pageConfig.fieldNotUpdatedMsg)(response);
        },
        success: () => {
            onSuccess();
            window.notificationService.showInfoNotification(pageConfig.fieldUpdatedMsg);
        }
    });
}

function addGroupsCheckboxListeners(table) {
    $(".group-checkbox").off("change");

    $(".group-checkbox").on("change", function () {
        const input = $(this);
        const id = this.dataset.groupid;

        if (!this.checked) {
            removeFromRoleGroup(id, () => {}, () => {
                input[0].checked = true; // keep checkbox selected on failure
            });
        } else {
            addToRoleGroup(id, () => {}, () => {
                input[0].checked = false; // keep checkbox unselected on failure
            });
        }
    });
}

function addToRoleGroup(id, onSuccess, onError) {
    const newUrl = `${pageConfig.roleGroupsUrl}addrole/${id}/${pageConfig.roleId}`;

    $.ajax({
        url: newUrl,
        method: "POST",
        headers: {
            "X-CSRF-TOKEN": window.token
        },
        error: () => {
            onError();
            errorHandler(pageConfig.fieldNotUpdatedMsg)();
        },
        success: () => {
            onSuccess();
            window.notificationService.showInfoNotification(pageConfig.fieldUpdatedMsg);
        }
    });
}

function removeFromRoleGroup(id, onSuccess, onError) {
    const newUrl = `${pageConfig.roleGroupsUrl}removerole/${id}/${pageConfig.roleId}`;

    $.ajax({
        url: newUrl,
        method: "POST",
        headers: {
            "X-CSRF-TOKEN": window.token
        },
        error: (response) => {
            onError();
            errorHandler(pageConfig.fieldNotUpdatedMsg)(response);
        },
        success: () => {
            onSuccess();
            window.notificationService.showInfoNotification(pageConfig.fieldUpdatedMsg);
        }
    });
}

/**
 * Delegated click listeners for links/buttons rendered inside the
 * ajax-loaded users/ous tab fragments (same fragments and .js-* hooks as
 * userroles/view.html).
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
        window.bulkAssignRoleModalService.start(pageConfig.roleId, pageConfig.roleName);
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

    $("#users_menu").on("click", "#listTableUsersAdd tbody .js-add-user-role-by-uuid", function () {
        userService.addUserRoleByUuid(this.dataset.uuid);
    });

    $("#ous_menu").on("click", ".js-add-ou-role", () => {
        orgUnitService.loadAddUserRoleFragment();
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
 * Manages the "assigned users" / "available users" tab for a user role,
 * plus the "add user role" modal.
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

    // Alias used by the shared user_user_role_modal after a role is added.
    loadRolesFragmentWhenReady() {
        this.loadRolesFragment();
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
                        data ? `<a href="#" class="js-add-user-role-by-uuid" data-uuid="${data}"><em class="fa fa-plus"></em></a>` : null
                    )
                }
            ];

            const table = new DatatableService().initDefaultServersideTable(
                "#listTableUsersAdd",
                `${this.config.userRestUrl}available/${this.config.roleId}`,
                columnDefOptions
            );

            const searchInput = table.table().container().querySelector(".dataTables_filter input");
            searchInput.focus();
        });
    }

    // Alias used by the shared user_user_role_modal after a role is added.
    loadAddUserRoleFragmentWhenReady() {
        this.loadAddUserRoleFragment();
    }

    deleteRoleAssignment(elem) {
        const parent = elem.parentElement;
        const assignmentId = parent.dataset.assignmentid;
        const user = parent.dataset.user;
        const roleName = parent.dataset.name;

        const endpoint = `${this.config.userRestUrl}${user}/removeassignment/USERROLE/DIRECT/${assignmentId}`;
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
        const caseNumber = parent.dataset.casenumber;
        const orgUnit = parent.dataset.ouuuid;
        const roleName = parent.dataset.name;

        $.ajax({
            method: "GET",
            url: `${this.config.userRestUrl}${user}/orgunits`,
            success: (response) => {
                window.userRoleEditModalService.showModal(
                    user, startDate, stopDate, "USERROLE", assignmentId, "DIRECT", orgUnit, response, caseNumber, roleName
                );
                window.userRoleEditModalService.loadPostponedConstraintsFragment(assignmentId, "USERROLE", user);
            },
            error: defaultErrorHandler
        });
    }

    addUserRole(elem) {
        this.#openAssignModal(elem.dataset.user);
    }

    addUserRoleByUuid(uuid) {
        this.#openAssignModal(uuid);
    }

    #openAssignModal(user) {
        $("#userUserRoleModal").load(`${this.config.uiUrl}fragments/${user}`, () => {
            const modal = $("#modal-positions");

            window.initUserRoleModalDatePickers();
            window.userRoleModalService.init();
            window.userRoleModalService.parentService = this;

            modal.modal({ backdrop: "static", keyboard: false });

            window.userRoleModalService.loadPostponedConstraintsFragment(this.config.roleId);
            $("#stopDatePicker").data("DateTimePicker").clear();
            $("#startDatePicker").data("DateTimePicker").clear();
            $("#startDatePicker").data("DateTimePicker").date(new Date());
            $("#assignUserRoleName").text(this.config.roleName);

            modal.attr("roleid", this.config.roleId);
            modal.attr("userUuid", user);
        });
    }
}

/**
 * Manages the "assigned OUs" / "available OUs" tab for a user role.
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

    loadAddUserRoleFragment() {
        $("#ous_menu").load(`${this.config.uiUrl}${this.config.roleId}/availableOrgUnitsFragment`, () => {
            fragShowDataTableFun("#listTableOusAdd", 0);
        });
    }

    deleteRoleAssignment(elem) {
        const parent = elem.parentElement;
        const assignmentId = parent.dataset.assignmentid;
        const ou = parent.dataset.orgunit;
        const roleName = parent.dataset.name;

        const endpoint = `${this.config.urlOUs}${ou}/removeassignment/USERROLE/${assignmentId}`;
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

        $("#ouUserRoleEditModal").load(`${this.config.uiUrl}fragments/ou/${ou}?edit=true`, () => {
            window.initOuModalDatePickers();
            window.ouRoleAssignmentService.init();

            window.ouRolesEditModalService.assignmentId = assignmentId;
            window.ouRolesEditModalService.ouUuid = ou;
            window.ouRolesEditModalService.inherit = Number(assignmentType) === -2;
            window.ouRolesEditModalService.roleType = "role";
            window.ouRolesEditModalService.roleId = this.config.roleId;

            window.ouRolesEditModalService.showAssignModal(
                this.config.titlesEnabled, startDate, stopDate, assignmentType, assignmentId, this.config.roleName
            );
        });
    }

    addRoleAssignment(elem) {
        window.ouRolesModalService.roleType = "role";
        window.ouRolesModalService.roleId = this.config.roleId;
        window.ouRolesModalService.ouUuid = elem.dataset.orgunit;

        $("#ouUserRoleModal").load(`${this.config.uiUrl}fragments/ou/${window.ouRolesModalService.ouUuid}`, () => {
            window.initOuModalDatePickers();
            window.ouRoleAssignmentService.init();

            window.ouRolesModalService.showAssignModal(this.config.titlesEnabled, null, null, this.config.roleName);
        });
    }
}

/**
 * Manages the manager-action settings (require manager action, send to
 * substitutes, send to authorization managers) for a MANUAL-type role.
 */
class ManagerActionService {
    constructor(config) {
        this.config = config;
    }

    init() {
        $("#requireManagerActionCheckbox").on("change", (event) => {
            const checked = $(event.target).prop("checked");

            if (checked) {
                $("#requireManagerActionRow").attr("hidden", false);
            } else {
                $("#requireManagerActionRow").attr("hidden", true);
                $("#sendToSubstitutes").prop("checked", false);
                $("#sendToAuthorizationManagers").prop("checked", false);
            }

            this.save("requireManagerAction", checked);
        });

        $("#sendToAuthorizationManagers").on("change", (event) => {
            this.save("sendToAuthorizationManagers", $(event.target).prop("checked"));
        });

        $("#sendToSubstitutes").on("change", (event) => {
            this.save("sendToSubstitutes", $(event.target).prop("checked"));
        });
    }

    save(field, checked) {
        const updateUrl = `${this.config.url}manageraction/${this.config.roleId}/${field}?checked=${checked}`;

        $.ajax({
            url: updateUrl,
            method: "POST",
            headers: {
                "X-CSRF-TOKEN": window.token
            },
            error: defaultErrorHandler,
            success: () => {
                window.notificationService.showInfoNotification(this.config.fieldUpdatedMsg);
            }
        });
    }
}

/**
 * Manages the OU-filter checkbox, modal, and jsTree selection for a user role.
 */
class OUFilterService {
    constructor(config) {
        this.config = config;
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
        window.jsTreeService.initTree("#ou-filter-tree", {
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

        $("#ou-filter-tree").on("ready.jstree", () => {
            const treeInstance = window.jsTreeService.getInstance("#ou-filter-tree");

            treeInstance.get_json("#", { flat: true }).forEach((node) => {
                if (this.config.possibleFilterOus != null && !this.config.possibleFilterOus.includes(node.id)) {
                    treeInstance.disable_node(node.id);
                }
            });

            treeInstance.select_node(this.config.selectedFilterOUs);
        });

        let searchTimeout = false;
        $("#ou-filter-tree-search").on("keyup", () => {
            if (searchTimeout) {
                clearTimeout(searchTimeout);
            }

            searchTimeout = setTimeout(() => {
                const searchValue = $("#ou-filter-tree-search").val();
                window.jsTreeService.getInstance("#ou-filter-tree").search(searchValue);
            }, 400);
        });
    }

    handleOUFilterBtnClick() {
        $("#modal-ou-filter").modal("show");
    }

    handleOUFilterCheckbox() {
        const checked = $("#ouFilterEnabled-checkbox").prop("checked");

        $.ajax({
            url: "/rest/userroles/ouFilterEnabled",
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
        const selected = window.jsTreeService.getInstance("#ou-filter-tree").get_selected();

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

async function handleUserRoleRequesterPermissionChange() {
    const roleId = String(this.dataset.roleid);
    const selectedValues = Array.from(this.selectedOptions).map((option) => option.value);
    requestApproveService.handleRequesterRestrictions();

    const response = await fetch(`${pageConfig.url}${roleId}/requester`, {
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

async function handleUserRoleApproverPermissionChange() {
    const roleId = String(this.dataset.roleid);
    const selectedValues = Array.from(this.selectedOptions).map((option) => option.value);
    requestApproveService.handleApproverRestrictions();

    const response = await fetch(`${pageConfig.url}${roleId}/approver`, {
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
