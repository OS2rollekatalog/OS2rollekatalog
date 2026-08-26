document.addEventListener("DOMContentLoaded", () => {
    const configElement = document.getElementById("itsystem-edit-config");
    if (!configElement) {
        return;
    }

    const config = JSON.parse(configElement.textContent);

    // The token is read from the meta tag at runtime, same as the original inline script.
    // Assigned on window so other scripts relying on a global "token" (e.g. NetworkService's
    // default constructor parameter) keep working exactly as before.
    window.token = $("meta[name='_csrf']").attr("content");
    const token = window.token;

    const select2Service = new Select2Service();
    const sweetAlertService = new SweetAlertService();
    const jsTreeService = new JsTreeService();
    const requestApproveService = new RequestApproveService();

    window.currentTable = undefined;
    let readonly = config.readonly;

    $('[data-toggle="popover"]').popover({ container: "body" });

    const ouFilterService = createOuFilterService();
    ouFilterService.init();

    $("#name").change(handleChangeOnInput);
    $("#email").change(handleChangeOnInputEmail);
    $("#advisEmail").change(handleChangeOnInputAdvisEmail);
    $("#manualEffectuationEnabled-checkbox").change(handleChangeOnManualEffectuation);
    $("#notificationEmail").change(handleChangeOnInputNotificationEmail);
    $("#notes").change(handleChangeOnInputNotes);
    $("#paused-checkbox").change(handleChangeOnPaused);
    $("#readonly-checkbox").change(handleChangeOnReadOnly);
    $("#canEditThroughApi-checkbox").change(handleChangeOnCanEditThroughApi);
    $("#hidden-checkbox").change(handleChangeOnHidden);
    $("#att-exempt-checkbox").change(handleChangeAttExempt);
    $("#accessBlocked-checkbox").change(handleChangeOnAccessBlocked);
    $("#apiManagedRoleAssignments-checkbox").change(handleChangeOnApiManagedRoleAssignmentsUrl);
    $("#subscribedToCheckbox").change(toggleDisabledCheckbox);
    $("#requesterSettingsSelect").change(handleChangeOnITSystemRequesterPermission);
    $("#approverSettingsSelect").change(handleChangeOnITsystemApproverPermission);
    $("#subscribedTo").change(handleChangeOnSubscribedTo);
    $(".useronly-checkbox").change(handleUserroleChange);
    $(".sensitive-checkbox").change(handleUserroleChange);
    $(".extra-sensitive-checkbox").change(handleUserroleChange);
    $(".roleAssignmentAttestationByAttestationResponsible-checkbox").change(handleUserroleChange);

    initPersonSelect2("#attestationResponsibleSelect", config.urls.attestationResponsible);
    initPersonSelect2("#systemOwnerSelect", config.urls.systemOwner);

    $("#kitosItSystemSelector").change(handleChangeOnKitosITSystem);
    select2Service.initSelect("#kitosItSystemSelector", { placeholder: "", allowClear: true });

    const manualWelcomeEmailTemplateService = createManualWelcomeEmailTemplateService();
    manualWelcomeEmailTemplateService.init();

    // Re-assign row-level listeners whenever DataTables redraws the user role table
    $(".userRoleTable").on("draw.dt", () => {
        $(".useronly-checkbox").off("change").change(handleUserroleChange);
        $(".sensitive-checkbox").off("change").change(handleUserroleChange);
        $(".extra-sensitive-checkbox").off("change").change(handleUserroleChange);
        $(".roleAssignmentAttestationByAttestationResponsible-checkbox").off("change").change(handleUserroleChange);

        initRoleRequestApproveSelects();
    });

    select2Service.initSelect("#requesterSettingsSelect, #approverSettingsSelect", {
        placeholder: "",
        allowClear: true,
        multiple: true
    });

    if (config.subscribedToMaster) {
        $("#subscribedTo").show();
        $(".operationsCol").addClass("subscribed");
        $("#addRoleButtonField").hide();
        $("#name").prop("readonly", true);
    } else {
        $("#subscribedTo").hide();
    }

    if ($(".error").length > 0) {
        if (config.itSystemType === "AD") {
            $("#modal-ad-systemrole").modal("show");
        }

        if (config.itSystemType === "MANUAL" || config.itSystemType === "SAML" || config.itSystemType === "KOMBIT") {
            $("#modal-saml-systemrole").modal("show");
        }
    }

    initRoleRequestApproveSelects();

    // Initialize column toggle for the user role table
    const userRoleTableEl = $(".userRoleTable");
    if (userRoleTableEl.length > 0) {
        const dropdown = userRoleTableEl.closest(".tab-pane").find(".dropdown-menu");
        const table = userRoleTableEl.DataTable();

        // Map data-col-name to data-cid for compatibility with dataTablesToggleColumn
        dropdown.find("[data-col-name]").each(function () {
            const colName = $(this).data("col-name");
            const column = table.column((idx, data, node) => $(node).data("col-name") === colName);
            if (column.length) {
                $(this).data("cid", column.index());
            }
        });

        window.currentTable = table;
        dataTablesRefreshIcons(dropdown);
    }

    $(document).on("click", ".dropdown-menu [data-col-name]", function (e) {
        e.stopPropagation();
        e.preventDefault();
        window.currentTable = $(this).closest(".tab-pane").find(".userRoleTable").DataTable();
        dataTablesToggleColumn(this);
        initRoleRequestApproveSelects();
    });

    // Listeners replacing the removed onclick attributes
    $(document).on("click", ".js-open-ad-role-modal", () => newAdRole());
    $(document).on("click", ".js-open-saml-role-modal", () => newSamlRole());
    $(document).on("click", ".js-convert-system-roles", () => convertSystemRolesToUserRoles());
    $(document).on("click", ".js-edit-ad-role", function () {
        editAdRole(this);
    });
    $(document).on("click", ".js-edit-saml-role", function () {
        editSamlRole(this);
    });
    $(document).on("click", ".js-delete-system-role", function (e) {
        e.preventDefault();
        openConfirmDeleteDialog(this);
    });
    $(document).on("click", ".js-remove-unused-roles", () => removeUnusedUserRoles());

    $("#universalFieldSet").hide();

    $("#adGroupType").on("change", () => {
        if ($("#adGroupType").val() !== "NONE") {
            $("#universalFieldSet").show();
        } else {
            $("#universalFieldSet").hide();
        }
    });

    $("#universalCheckbox").on("change", () => {
        $("#universalValue").val($("#universalCheckbox").val());
    });

    $("#modal-convert-systemroles").on("show.bs.modal", () => {
        // Reset to NAME_AND_DESCRIPTION as default
        $("#linkTypeNameAndDescription").prop("checked", true);
    });

    // ---------- Request/approve settings ----------

    function handleChangeOnITSystemRequesterPermission() {
        const requesterSelect = document.getElementById("requesterSettingsSelect");
        requestApproveService.handleRequesterRestrictions();

        const newRequesters = [];
        for (const option of requesterSelect.selectedOptions) {
            if (option.value !== undefined) {
                newRequesters.push(option.value);
            }
        }

        $.ajax({
            url: config.urls.requesterChange,
            method: "POST",
            data: {
                "requesterPermissions[]": newRequesters,
                "id": config.itSystemId
            },
            headers: { "X-CSRF-TOKEN": token },
            error: errorHandler(config.messages.fieldNotUpdated),
            success: () => {
                $.notify({ message: config.messages.fieldUpdated }, { status: "success", autoHideDelay: 2000 });
            }
        });
    }

    function handleChangeOnITsystemApproverPermission() {
        const approverSelect = document.getElementById("approverSettingsSelect");
        requestApproveService.handleApproverRestrictions();

        const newApprovers = [];
        for (const option of approverSelect.selectedOptions) {
            if (option.value !== undefined) {
                newApprovers.push(option.value);
            }
        }

        $.ajax({
            url: config.urls.approverChange,
            method: "POST",
            data: {
                "approverPermissions": newApprovers,
                "id": config.itSystemId
            },
            headers: { "X-CSRF-TOKEN": token },
            error: errorHandler(config.messages.fieldNotUpdated),
            success: () => {
                $.notify({ message: config.messages.fieldUpdated }, { status: "success", autoHideDelay: 2000 });
            }
        });
    }

    function initRoleRequestApproveSelects() {
        // Destroy existing select2 instances to avoid duplicates
        $(".requester-role-select.select2-hidden-accessible").select2("destroy");
        $(".approver-role-select.select2-hidden-accessible").select2("destroy");

        select2Service.initSelect(".requester-role-select", { placeholder: "", allowClear: true, width: "100%" });
        select2Service.initSelect(".approver-role-select", { placeholder: "", allowClear: true, width: "100%" });

        // Unbind first to avoid duplicate handlers
        $(".requester-role-select").off("change.requestApprove");
        $(".approver-role-select").off("change.requestApprove");

        $(".requester-role-select").on("change.requestApprove", function () {
            const roleId = this.dataset.roleid;
            const selected = $(this).val() || [];

            $.ajax({
                url: `${config.urls.userRole}${roleId}/requester`,
                method: "POST",
                contentType: "application/json",
                data: JSON.stringify({ requesterPermission: selected }),
                headers: { "X-CSRF-TOKEN": token },
                success: () => {
                    $.notify({ message: config.messages.fieldUpdated }, { status: "success", autoHideDelay: 2000 });
                },
                error: errorHandler(config.messages.fieldNotUpdated)
            });
        });

        $(".approver-role-select").on("change.requestApprove", function () {
            const roleId = this.dataset.roleid;
            const selected = $(this).val() || [];

            $.ajax({
                url: `${config.urls.userRole}${roleId}/approver`,
                method: "POST",
                contentType: "application/json",
                data: JSON.stringify({ approverPermission: selected }),
                headers: { "X-CSRF-TOKEN": token },
                success: () => {
                    $.notify({ message: config.messages.fieldUpdated }, { status: "success", autoHideDelay: 2000 });
                },
                error: errorHandler(config.messages.fieldNotUpdated)
            });
        });
    }

    // ---------- Field change handlers ----------

    function initPersonSelect2(selector, saveUrl) {
        select2Service.initPersonSearchSelect(selector, config.urls.personSearch);

        $(selector).on("change", () => {
            const uuids = $(selector).val() || [];

            $.ajax({
                url: saveUrl,
                method: "POST",
                data: {
                    "id": config.itSystemId,
                    "uuids": uuids
                },
                traditional: true,
                headers: { "X-CSRF-TOKEN": token },
                error: errorHandler(config.messages.fieldNotUpdated),
                success: () => {
                    $.notify({ message: config.messages.fieldUpdated }, { status: "success", autoHideDelay: 2000 });
                }
            });
        });
    }

    function handleChangeOnKitosITSystem() {
        const kitosITSystem = $("#kitosItSystemSelector").val();

        $.ajax({
            url: config.urls.kitosItSystem,
            method: "POST",
            data: {
                "kitosITSystemId": kitosITSystem,
                "id": config.itSystemId
            },
            headers: { "X-CSRF-TOKEN": token },
            error: errorHandler(config.messages.fieldNotUpdated),
            success: () => {
                $.notify({ message: config.messages.fieldUpdated }, { status: "success", autoHideDelay: 2000 });
                setTimeout(() => location.reload(), 1000);
            }
        });
    }

    function handleChangeOnInput() {
        const objName = $("#name").val();

        $.ajax({
            url: config.urls.name,
            method: "POST",
            data: {
                "name": objName,
                "id": config.itSystemId
            },
            headers: { "X-CSRF-TOKEN": token },
            error: errorHandler(config.messages.fieldNotUpdated),
            success: () => {
                $.notify({ message: config.messages.fieldUpdated }, { status: "success", autoHideDelay: 2000 });
            }
        });
    }

    function handleChangeOnInputEmail() {
        const objEmail = $("#email").val();

        $.ajax({
            url: config.urls.email,
            method: "POST",
            data: {
                "email": objEmail,
                "id": config.itSystemId
            },
            headers: { "X-CSRF-TOKEN": token },
            error: (response) => {
                $("#emailValidationFail").show();
                errorHandler(config.messages.fieldNotUpdatedEmail)(response);
            },
            success: () => {
                $("#emailValidationFail").hide();
                $.notify({ message: config.messages.fieldUpdated }, { status: "success", autoHideDelay: 2000 });
            }
        });
    }

    function handleChangeOnInputAdvisEmail() {
        const objAdvisEmail = $("#advisEmail").val();

        $.ajax({
            url: config.urls.advisEmail,
            method: "POST",
            data: {
                "email": objAdvisEmail,
                "id": config.itSystemId
            },
            headers: { "X-CSRF-TOKEN": token },
            error: (response) => {
                $("#advisEmailValidationFail").show();
                errorHandler(config.messages.fieldNotUpdatedEmail)(response);
            },
            success: () => {
                $("#advisEmailValidationFail").hide();
                $.notify({ message: config.messages.fieldUpdated }, { status: "success", autoHideDelay: 2000 });
            }
        });
    }

    function handleChangeOnManualEffectuation() {
        const enabled = $("#manualEffectuationEnabled-checkbox").is(":checked");
        const dto = {
            "id": config.itSystemId,
            "manualEffectuationEnabled": enabled
        };

        $.ajax({
            url: config.urls.manualEffectuation,
            method: "POST",
            contentType: "application/json",
            data: JSON.stringify(dto),
            headers: { "X-CSRF-TOKEN": token },
            error: errorHandler(config.messages.fieldNotUpdated),
            success: () => {
                toggleManualWelcomeEmailFieldset(enabled);
                $.notify({ message: config.messages.fieldUpdated }, { status: "success", autoHideDelay: 2000 });
            }
        });
    }

    function toggleManualWelcomeEmailFieldset(enabled) {
        if (enabled) {
            $("#manualWelcomeEmailFieldset").show();
        } else {
            $("#manualWelcomeEmailFieldset").hide();
        }
    }

    function handleChangeOnInputNotificationEmail() {
        const objNotificationEmail = $("#notificationEmail").val();

        $.ajax({
            url: config.urls.notificationEmail,
            method: "POST",
            data: {
                "email": objNotificationEmail,
                "id": config.itSystemId
            },
            headers: { "X-CSRF-TOKEN": token },
            error: errorHandler(config.messages.fieldNotUpdated),
            success: () => {
                $.notify({ message: config.messages.fieldUpdated }, { status: "success", autoHideDelay: 2000 });
            }
        });
    }

    function handleChangeOnPaused() {
        const objPaused = $("#paused-checkbox").is(":checked");

        $.ajax({
            url: config.urls.paused,
            method: "POST",
            data: {
                "paused": objPaused,
                "id": config.itSystemId
            },
            headers: { "X-CSRF-TOKEN": token },
            error: errorHandler(`${config.messages.fieldNotUpdated}`),
            success: () => {
                $.notify({ message: config.messages.fieldUpdated }, { status: "success", autoHideDelay: 2000 });
            }
        });
    }

    function handleChangeOnReadOnly() {
        const objReadonly = $("#readonly-checkbox").is(":checked");

        $.ajax({
            url: config.urls.readonly,
            method: "POST",
            data: {
                "readonly": objReadonly,
                "id": config.itSystemId
            },
            headers: { "X-CSRF-TOKEN": token },
            error: errorHandler(`${config.messages.fieldNotUpdated}`),
            success: () => {
                readonly = objReadonly;
                toggleAddAdRoleBtnDisabled(objReadonly);
                toggleConvertSystemRolesToUserRolesBtnDisabled(objReadonly);
                toggleRemoveUnusedRolesBtnDisabled(objReadonly);
                $.notify({ message: config.messages.fieldUpdated }, { status: "success", autoHideDelay: 2000 });
                setTimeout(() => location.reload(), 1000);
            }
        });
    }

    function toggleAddAdRoleBtnDisabled(objReadonly) {
        if (objReadonly) {
            $("#addAdRoleBtn").attr("disabled", true);
        } else {
            $("#addAdRoleBtn").removeAttr("disabled");
        }
    }

    function toggleConvertSystemRolesToUserRolesBtnDisabled(objReadonly) {
        if (objReadonly) {
            $("#convertSystemRolesToUserRolesBtn").attr("disabled", true);
        } else {
            $("#convertSystemRolesToUserRolesBtn").removeAttr("disabled");
        }
    }

    function toggleRemoveUnusedRolesBtnDisabled(objReadonly) {
        if (objReadonly) {
            $("#removeUnusedRolesBtn").attr("disabled", true);
        } else {
            $("#removeUnusedRolesBtn").removeAttr("disabled");
        }
    }

    function handleChangeOnCanEditThroughApi() {
        const objCanEditApi = $("#canEditThroughApi-checkbox").is(":checked");

        $.ajax({
            url: config.urls.canEditThroughApi,
            method: "POST",
            data: {
                "canEditThroughApi": objCanEditApi,
                "id": config.itSystemId
            },
            headers: { "X-CSRF-TOKEN": token },
            error: errorHandler(config.messages.fieldNotUpdated),
            success: () => {
                $.notify({ message: config.messages.fieldUpdated }, { status: "success", autoHideDelay: 2000 });
            }
        });
    }

    function handleChangeAttExempt() {
        const objAttExempt = $("#att-exempt-checkbox").is(":checked");

        $.ajax({
            url: config.urls.attestationExempt,
            method: "POST",
            data: {
                "attestationExempt": objAttExempt,
                "id": config.itSystemId
            },
            headers: { "X-CSRF-TOKEN": token },
            error: errorHandler(config.messages.fieldNotUpdated),
            success: () => {
                $.notify({ message: config.messages.fieldUpdated }, { status: "success", autoHideDelay: 2000 });
            }
        });
    }

    function handleChangeOnHidden() {
        const objHidden = $("#hidden-checkbox").is(":checked");

        $.ajax({
            url: config.urls.hidden,
            method: "POST",
            data: {
                "hidden": objHidden,
                "id": config.itSystemId
            },
            headers: { "X-CSRF-TOKEN": token },
            error: errorHandler(config.messages.fieldNotUpdated),
            success: () => {
                $.notify({ message: config.messages.fieldUpdated }, { status: "success", autoHideDelay: 2000 });
            }
        });
    }

    function handleChangeOnApiManagedRoleAssignmentsUrl() {
        const checkboxValue = $("#apiManagedRoleAssignments-checkbox").is(":checked");

        $.ajax({
            url: config.urls.apiManagedRoleAssignments,
            method: "POST",
            data: {
                "apiManagedRoleAssignments": checkboxValue,
                "id": config.itSystemId
            },
            headers: { "X-CSRF-TOKEN": token },
            error: errorHandler(config.messages.fieldNotUpdated),
            success: () => {
                $.notify({ message: config.messages.fieldUpdated }, { status: "success", autoHideDelay: 2000 });
            }
        });
    }

	function handleChangeOnAccessBlocked() {
		const objAccessBlocked = $("#accessBlocked-checkbox").is(":checked");

		if (objAccessBlocked === true) {
			sweetAlertService.confirm(
				config.messages.accessBlockedConfirmTitle,
				config.messages.accessBlockedConfirmBody,
				config.messages.yes,
				config.messages.cancel,
				() => {
					blockAccess(objAccessBlocked);
				},
				{
					onCancel: () => {
						$("#accessBlocked-checkbox").attr("checked", false);
					}
				}
			);
		} else {
			blockAccess(objAccessBlocked);
		}
	}

    function blockAccess(objAccessBlocked) {
        $.ajax({
            url: config.urls.accessBlocked,
            method: "POST",
            data: {
                "accessBlocked": objAccessBlocked,
                "id": config.itSystemId
            },
            headers: { "X-CSRF-TOKEN": token },
            error: errorHandler(config.messages.fieldNotUpdated),
            success: () => {
                $.notify({ message: config.messages.fieldUpdated }, { status: "success", autoHideDelay: 2000 });
            }
        });
    }

    function handleChangeOnInputNotes() {
        const objNotes = $("#notes").val();

        $.ajax({
            url: config.urls.notes,
            method: "POST",
            data: {
                "notes": objNotes,
                "id": config.itSystemId
            },
            headers: { "X-CSRF-TOKEN": token },
            error: errorHandler(config.messages.fieldNotUpdatedNotes),
            success: () => {
                $.notify({ message: config.messages.fieldUpdated }, { status: "success", autoHideDelay: 2000 });
            }
        });
    }

    function handleChangeOnSubscribedTo() {
        const subscribedTo = $("#subscribedToCheckbox").is(":checked");
        let objItSystemMaster = $("#subscribedTo").val();

        const optionId = $("#subscribedTo").children(":selected").attr("id");
        if (optionId === "defaultOption" || !subscribedTo) {
            // dirty workaround for submitting null values
            objItSystemMaster = "null";
        }

        $.ajax({
            url: config.urls.subscribedTo,
            method: "POST",
            data: {
                "masterId": objItSystemMaster,
                "id": config.itSystemId
            },
            headers: { "X-CSRF-TOKEN": token },
            error: errorHandler(config.messages.fieldNotUpdated),
            success: () => {
                $.notify({ message: config.messages.fieldUpdated }, { status: "success", autoHideDelay: 2000 });
            }
        });
    }

    function toggleDisabledCheckbox() {
        $("#subscribedTo").toggle();
        $(".operationsCol").toggleClass("subscribed");
        $("#addRoleButtonField").toggle();
        $("#name").is("[readonly]") ? $("#name").prop("readonly", false) : $("#name").prop("readonly", true);

        handleChangeOnSubscribedTo();
    }

    // ---------- System role modals ----------

    function newAdRole() {
        if (readonly) {
            return;
        }

        $("#adSystemRoleId").val("0");
        $("#modal-ad-systemrole").modal("show");

        $("#adSystemRoleName").val("");
        $("#adSystemRoleIdentifier").val("");
        $("#adSystemRoleIdentifier").prop("readonly", false);
        $("#adSystemRoleDescription").val("");
        $("#adSystemRoleWeight").val("1");
        $("#adSystemRoleMaximumAssignments").val("");

        $("#adGroupType").val("NONE");
        $("#universalFieldSet").hide();
        $("#adSystemRoleName").focus();
    }

    function editAdRole(roleRow) {
        const id = $(roleRow).data("id");
        const tds = $(roleRow).parent().parent().find("td");

        $("#adSystemRoleId").val(id);
        $("#adSystemRoleName").val(tds.get(0).innerHTML);
        $("#adSystemRoleIdentifier").val(tds.get(1).innerHTML);
        $("#adSystemRoleIdentifier").prop("readonly", true);
        $("#adSystemRoleDescription").val(tds.get(2).innerHTML);
        $("#adSystemRoleWeight").val(tds.get(4).innerHTML);
        $("#adSystemRoleMaximumAssignments").val(tds.get(5).innerHTML);
        $("#createanduniversalfieldset").hide();

        $("#modal-ad-systemrole").modal("show");
        $("#adGroupType").val("NONE");
    }

    function newSamlRole() {
        $("#samlSystemRoleId").val("0");
        $("#modal-saml-systemrole").modal("show");

        $("#samlSystemRoleName").val("");
        $("#samlSystemRoleIdentifier").val("");
        $("#samlSystemRoleIdentifier").prop("readonly", false);
        $("#samlSystemRoleDescription").val("");
        $("#samlSystemRoleWeight").val("1");
        $("#samlSystemRoleMaximumAssignments").val("");

        $("#samlSystemRoleName").focus();

        $("#adGroupType").val("NONE");
        $("#universalFieldSet").hide();
    }

    function editSamlRole(roleRow) {
        const id = $(roleRow).data("id");
        const tds = $(roleRow).parent().parent().find("td");

        $("#samlSystemRoleId").val(id);
        $("#samlSystemRoleName").val(tds.get(0).innerHTML);
        $("#samlSystemRoleIdentifier").val(tds.get(1).innerHTML);
        $("#samlSystemRoleIdentifier").prop("readonly", true);
        $("#samlSystemRoleDescription").val(tds.get(2).innerHTML);

        if (config.itSystemType === "SAML") {
            $("#samlSystemRoleWeight").val(tds.get(4).innerHTML);
            $("#samlSystemRoleMaximumAssignments").val(tds.get(5).innerHTML);
        } else {
            $("#samlSystemRoleMaximumAssignments").val(tds.get(4).innerHTML);
        }

        $("#modal-saml-systemrole").modal("show");
    }

    function openConfirmDeleteDialog(roleRow) {
        const itSystemIdFromForm = $("#id").val();
        const id = $(roleRow).data("id");
        const redirectUrl = config.urls.editPageBase + itSystemIdFromForm;
        const deleteUri = config.urls.systemRoleDeleteBase + id;

        sweetAlertService.confirm(
            config.messages.deleteRoleConfirmTitle,
            config.messages.deleteRoleConfirmBody,
            config.messages.delete,
            config.messages.cancel,
            () => {
                $.ajax({
                    type: "POST",
                    headers: { "X-CSRF-TOKEN": token },
                    url: deleteUri,
                    success: () => {
                        window.location.href = redirectUrl;
                    },
                    error: errorHandler(config.messages.roleNotDeleted)
                });
            }
        );
    }

    function removeUnusedUserRoles() {
        if (readonly) {
            return;
        }

        sweetAlertService.confirm(
            config.messages.unusedRolesConfirmTitle,
            config.unusedCount > 0 ? config.messages.unusedRolesConfirmBody : config.messages.unusedRolesNoRolesBody,
            config.messages.delete,
            config.messages.cancel,
            () => {
                $.ajax({
                    type: "POST",
                    headers: { "X-CSRF-TOKEN": token },
                    url: config.urls.unusedUserRoleDelete,
                    success: () => {
                        location.reload(true);
                    },
                    error: errorHandler(config.messages.rolesNotDeleted)
                });
            },
            { customConfig: { showConfirmButton: config.unusedCount > 0 } }
        );
    }

    function convertSystemRolesToUserRoles() {
        const objReadonly = $("#readonly-checkbox").is(":checked");
        if (!objReadonly) {
            $("#modal-convert-systemroles").modal("show");
        }
    }

    function handleUserroleChange() {
        const roleId = String(this.dataset.roleid);
        const fieldName = String(this.dataset.fieldname);
        const activeParam = this.checked ? "?active=true" : "?active=false";
        const updateUrl = `${config.urls.userRole}flag/${roleId}/${fieldName}${activeParam}`;

        $.ajax({
            url: updateUrl,
            method: "POST",
            headers: { "X-CSRF-TOKEN": token },
            error: defaultErrorHandler,
            success: () => {
                $.notify({ message: config.messages.fieldUpdated }, { status: "success", autoHideDelay: 4000 });
            }
        });
    }

    // ---------- OU filter ----------

    function createOuFilterService() {
        const service = {};

        service.init = () => {
            $("#ouFilterEnabled-checkbox").change(service.handleOUFilterCheckbox);
            $("#ouFilterBtn").click(service.handleOUFilterBtnClick);
            $("#oufilterSave").click(service.saveOUs);

            $("#modal-ou-filter").on("shown.bs.modal", () => {
                $("#ou-tree-search").focus();
            });

            service.toggleOUFilter();

            $("#modal-ou-filter").on("shown.bs.modal", () => {
                $("#ou-filter-tree-search").focus();
            });

            jsTreeService.initTree("#ou-filter-tree", {
                core: {
                    data: config.allOUs,
                    themes: { icons: false }
                },
                search: {
                    show_only_matches: true,
                    search_callback: (str, node) => node.text.toUpperCase().startsWith(str.toUpperCase())
                },
                checkbox: {
                    keep_selected_style: false,
                    three_state: false,
                    cascade: "undefined"
                },
                plugins: ["checkbox", "search"]
            });

            // searching in the JSTree
            let searchTimeout = false;
            $("#ou-filter-tree-search").keyup(() => {
                if (searchTimeout) {
                    clearTimeout(searchTimeout);
                }

                searchTimeout = setTimeout(() => {
                    const searchValue = $("#ou-filter-tree-search").val();
                    jsTreeService.getInstance("#ou-filter-tree").search(searchValue);
                }, 400);
            });

            // selecting in the JSTree
            $("#ou-filter-tree").on("ready.jstree", () => {
                $("#ou-filter-tree").jstree("select_node", config.selectedOUs);
            });
        };

        service.handleOUFilterBtnClick = () => {
            $("#modal-ou-filter").modal("show");
        };

        service.handleOUFilterCheckbox = () => {
            const checked = $("#ouFilterEnabled-checkbox").prop("checked");

            $.ajax({
                url: config.urls.ouFilterEnabled,
                method: "POST",
                data: {
                    "ouFilterEnabled": checked,
                    "id": config.itSystemId
                },
                headers: { "X-CSRF-TOKEN": token },
                error: errorHandler(config.messages.fieldNotUpdatedOUFilter),
                success: () => {
                    $.notify({ message: config.messages.fieldUpdated }, { status: "success", autoHideDelay: 2000 });
                }
            });

            service.toggleOUFilter();
        };

        service.toggleOUFilter = () => {
            const checked = $("#ouFilterEnabled-checkbox").prop("checked");

            if (checked) {
                $("#ouFilterButtonField").show();
            } else {
                $("#ouFilterButtonField").hide();
            }
        };

        service.saveOUs = () => {
            const selected = jsTreeService.getInstance("#ou-filter-tree").get_selected();

            $.ajax({
                url: `${config.urls.restItSystem}oufilter`,
                method: "POST",
                headers: {
                    "X-CSRF-TOKEN": token,
                    "Content-Type": "application/json"
                },
                data: JSON.stringify({
                    "id": config.itSystemId,
                    "selectedOUs": selected
                }),
                error: defaultErrorHandler,
                success: () => {
                    $("#modal-ou-filter").modal("hide");
                    location.reload();
                }
            });
        };

        return service;
    }

    // ---------- Manual welcome email template (fold-out) ----------

    function createManualWelcomeEmailTemplateService() {
        const service = {};
        let summernoteInitialized = false;
        let currentOperation = "ASSIGN";
        const templateIdsByOperation = {};

        service.init = () => {
            $("#manualWelcomeEmailCollapse").on("show.bs.collapse", () => {
                $("#manualWelcomeEmailCaret").removeClass("fa-caret-right").addClass("fa-caret-down");
                service.load(currentOperation);
            });

            $("#manualWelcomeEmailCollapse").on("hide.bs.collapse", () => {
                $("#manualWelcomeEmailCaret").removeClass("fa-caret-down").addClass("fa-caret-right");
            });

            $(".manualEffectuationEmailTab-link").on("shown.bs.tab", (event) => {
                currentOperation = $(event.target).data("operation");
                service.load(currentOperation);
            });

            $("#manualWelcomeEmailTemplateSave").on("click", () => service.save(false));
            $("#manualWelcomeEmailTemplateTest").on("click", () => service.save(true));
            $("#manualWelcomeEmailTemplateEnabled-checkbox").on("change", () => service.save(false));
        };

        service.load = (operation) => {
            $.ajax({
                url: config.urls.manualWelcomeEmailGet + "?operation=" + operation,
                method: "GET",
                headers: { "X-CSRF-TOKEN": token }
            }).done((dto) => {
                templateIdsByOperation[operation] = dto.id;

                $("#manualWelcomeEmailTemplateEnabled-checkbox").prop("checked", dto.enabled);
                $("#manualWelcomeEmailTemplateTitle").val(dto.title);
                $("#manualWelcomeEmailTemplateNotes").val(dto.notes);

                if (!summernoteInitialized) {
                    $("#manualWelcomeEmailTemplateMessage").val(dto.message);
                    $("#manualWelcomeEmailTemplateMessage").summernote({
                        height: 320,
                        toolbar: [
                            ["font", ["bold", "italic", "underline"]],
                            ["para", ["ul", "ol"]],
                            ["insert", ["picture", "link"]],
                            ["view", ["codeview"]]
                        ],
                        maximumImageFileSize: 100 * 1024,
                        callbacks: {
                            onImageUploadError: () => {
                                swal({
                                    title: config.messages.swalImageTitle,
                                    text: config.messages.swalImageText,
                                    confirmButtonColor: "#4765a0",
                                    confirmButtonText: config.messages.swalImageOk
                                });
                            }
                        },
                        dialogsInBody: true
                    });
                    summernoteInitialized = true;
                } else {
                    $("#manualWelcomeEmailTemplateMessage").summernote("code", dto.message ?? "");
                }
            }).fail(errorHandler(config.messages.emailTemplateFailure));
        };

        service.save = (tryEmail) => {
            const dto = {
                "id": templateIdsByOperation[currentOperation],
                "itSystemId": config.itSystemId,
                "operation": currentOperation,
                "title": $("#manualWelcomeEmailTemplateTitle").val(),
                "message": $("#manualWelcomeEmailTemplateMessage").summernote("code"),
                "notes": $("#manualWelcomeEmailTemplateNotes").val(),
                "enabled": $("#manualWelcomeEmailTemplateEnabled-checkbox").is(":checked")
            };

            $.ajax({
                method: "POST",
                url: config.urls.manualWelcomeEmail + "?tryEmail=" + tryEmail,
                headers: {
                    "content-type": "application/json",
                    "X-CSRF-TOKEN": token
                },
                data: JSON.stringify(dto)
            }).done((data) => {
                if (data) {
                    $.notify({ message: data }, { status: "success", autoHideDelay: 2000 });
                } else {
                    $.notify({ message: config.messages.emailTemplateSuccess }, { status: "success", autoHideDelay: 2000 });
                }
            }).fail(errorHandler(config.messages.emailTemplateFailure));
        };

        return service;
    }
});
