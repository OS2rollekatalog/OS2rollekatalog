$(document).ready(function () {
	const configElement = document.getElementById("adsync-config");
	if (!configElement) {
		return;
	}

    const config = JSON.parse(document.getElementById("adsync-config").textContent);
    const token = $("meta[name='_csrf']").attr("content");
    const settings = config.settings;

    const clientId = config.clientId;
    const url = config.url;
    const userRoleRestUrl = config.userRoleRestUrl;
    const userRoleBaseUrl = config.userRoleBaseUrl;
    const swalTitleTxt = config.swalTitle;
    const swalBodyTxt = config.swalBody;
    const updateSettingsConfirmTxt = config.confirmText;
    const updateSettingsCancelTxt = config.cancelText;
    const updateFailedMsg = config.failMsg;
    const settingsUpdatedMsg = config.successMsg;
    const simpleModalHeaderAttribute = config.modalHeaderAttribute;
    const simpleModalHeaderFilter = config.modalHeaderFilter;
    const simpleModalValueLabelAttribute = config.modalValueLabelAttribute;
    const simpleModalValueLabelFilter = config.modalValueLabelFilter;

    let membershipSyncFeatureAttributeMap = [];
    let membershipSyncFeatureFilterMap = [];
    let backSyncFeatureOUs = [];
    let itSystemGroupFeatureSystemMap = [];
    let readonlyItSystemFeatureSystemMap = [];
    let itSystemGroupFeatureRoleMap = [];

    // ── ModalService ────────────────────────────────────────────────────

    const modalService = {
        openSimpleModal: function (type) {
            if (type === "attribute") {
                $("#simpleModalTitle").text(simpleModalHeaderAttribute);
                $("#simpleModalValueLabel").text(simpleModalValueLabelAttribute);
                $("#simpleModalType").val(type);
            } else if (type === "filter") {
                $("#simpleModalTitle").text(simpleModalHeaderFilter);
                $("#simpleModalValueLabel").text(simpleModalValueLabelFilter);
                $("#simpleModalType").val(type);
            }

            $(".simpleModalField").val("");
            $("#simpleModal").modal("show");
        },

        openBackSyncFeatureOUsModal: function () {
            $("#mapType").val("backSyncFeatureOUs");
            $("#ouDNLabel").attr("hidden", false);
            $("#groupDNLabel").attr("hidden", true);
            $(".itSystemModalField").val("");
            $("#itSystemModalError").attr("hidden", true);
            $(".select2ItSystems").val(null).trigger("change");
            $("#itSystemModal").modal("show");
        },

        openItSystemGroupFeatureSystemAndRoleMapModal: function () {
            $(".systemAndUserRoleModalField").val("");
            $("#systemAndUserRoleModalError").attr("hidden", true);
            $("#systemAndUserRoleModalRole").val(null).trigger("change");
            $("#systemAndUserRoleModal").modal("show");
        },

        openReadonlyItSystemFeatureSystemMapModal: function () {
            $("#mapType").val("readonlyItSystemFeatureSystemMap");
            $("#ouDNLabel").attr("hidden", true);
            $("#groupDNLabel").attr("hidden", false);
            $(".itSystemModalField").val("");
            $("#itSystemModalError").attr("hidden", true);
            $(".select2ItSystems").val(null).trigger("change");
            $("#itSystemModal").modal("show");
        },

        addFromSimpleModal: function () {
            const type = $("#simpleModalType").val();
            const group = $("#simpleModalGroup").val();
            const attribute = $("#simpleModalAttribute").val();
            const value = $("#simpleModalValue").val();
            const fullValue = group + ";" + attribute + ";" + value;

            if (type === "attribute") {
                membershipSyncFeatureAttributeMap.push(fullValue);
                const row = settingService.createAttributeMapRow(fullValue);
                $("#membershipSyncFeatureAttributeMapList").append(row);
            } else if (type === "filter") {
                membershipSyncFeatureFilterMap.push(fullValue);
                const row = settingService.createFilterMapRow(fullValue);
                $("#membershipSyncFeatureFilterMapList").append(row);
            }

            settingService.addListeners();
            $("#simpleModal").modal("hide");
        },

        addFromITSystemModal: function () {
            const itSystem = $("#itSystemModalItSystem").val();
            const dn = $("#itSystemModalDN").val();
            const fullValue = itSystem + ";" + dn;
            const mapType = $("#mapType").val();

            if (itSystem === null || itSystem.length === 0 || dn === null || dn.length === 0) {
                $("#itSystemModalError").attr("hidden", false);
            } else {
                if (mapType === "backSyncFeatureOUs") {
                    backSyncFeatureOUs.push(fullValue);
                    const row = settingService.createBackSyncOURow(fullValue);
                    $("#backSyncFeatureOUsList").append(row);
                } else if (mapType === "readonlyItSystemFeatureSystemMap") {
                    readonlyItSystemFeatureSystemMap.push(fullValue);
                    const row = settingService.createReadonlySystemMapRow(fullValue);
                    $("#readonlyItSystemFeatureSystemMapList").append(row);
                }

                settingService.addListeners();
                $("#itSystemModal").modal("hide");
            }
        },

        addFromSystemAndUserRoleModal: function () {
            const userRole = $("#systemAndUserRoleModalRole").val();
            const itSystem = $("#systemAndUserRoleModalItSystem").val();
            const dn = $("#systemAndUserRoleModalDN").val();
            let type = "ITSYSTEM";

            if (userRole !== null && userRole.length !== 0) {
                type = "USERROLE";
            }

            if (type === "ITSYSTEM") {
                if (itSystem === null || itSystem.length === 0 || dn === null || dn.length === 0) {
                    $("#systemAndUserRoleModalError").attr("hidden", false);
                    return;
                }
                const fullValue = itSystem + ";" + dn;
                itSystemGroupFeatureSystemMap.push(fullValue);
            } else if (type === "USERROLE") {
                if (userRole === null || userRole.length === 0 || dn === null || dn.length === 0) {
                    $("#systemAndUserRoleModalError").attr("hidden", false);
                    return;
                }
                const fullValue = userRole + ";" + dn;
                if (!window.roleMappingToItSystem) {
                    window.roleMappingToItSystem = {};
                }
                window.roleMappingToItSystem[fullValue] = itSystem;
                itSystemGroupFeatureRoleMap.push(fullValue);
            }

            settingService.updateMappingsTable();
            $("#systemAndUserRoleModal").modal("hide");
        }
    };

    // ── Helpers ──────────────────────────────────────────────────────────

    function extractCNFromDN(dn) {
        if (!dn) {
            return dn;
        }
        const match = dn.match(/CN=([^,]+)/);
        return match ? match[1] : dn;
    }

    // ── SettingService ──────────────────────────────────────────────────

    const settingService = {
        init: function () {
            settingService.addListeners();
            membershipSyncFeatureAttributeMap = settings.membershipSyncFeatureAttributeMap || [];
            membershipSyncFeatureFilterMap = settings.membershipSyncFeatureFilterMap || [];
            backSyncFeatureOUs = settings.backSyncFeatureOUs || [];
            itSystemGroupFeatureSystemMap = settings.itSystemGroupFeatureSystemMap || [];
            readonlyItSystemFeatureSystemMap = settings.readonlyItSystemFeatureSystemMap || [];
            itSystemGroupFeatureRoleMap = settings.itSystemGroupFeatureRoleMap || [];

            $(".select2ItSystems").select2({
                placeholder: "",
                allowClear: true
            });
            settingService.initSystemAndUserRoleModalRoleSelect($("#systemAndUserRoleModalItSystem").val());

            $("#systemAndUserRoleModalItSystem").on("change", function () {
                settingService.initSystemAndUserRoleModalRoleSelect($("#systemAndUserRoleModalItSystem").val());
            });

            this.refreshAttributeMapTable();
            this.refreshFilterMapTable();
            this.refreshBackSyncOUTable();
            this.refreshReadonlySystemMapTable();
            this.updateMappingsTable();
        },

        updateMappingsTable: function () {
            const tableBody = $("#mappingsTableBody");
            tableBody.empty();

            itSystemGroupFeatureSystemMap.forEach(function (mapping) {
                const parts = mapping.split(";");
                const itSystemId = parts[0];
                const adGroupDN = parts[1];

                let itSystemText = $("#systemAndUserRoleModalItSystem option[value='" + itSystemId + "']").text();
                if (!itSystemText || itSystemText === "") {
                    itSystemText = "IT-system (ID: " + itSystemId + ")";
                }

                const adGroupName = extractCNFromDN(adGroupDN);

                const row = settingService.createMappingRow({
                    itSystemName: itSystemText,
                    roleName: "<em>&lt;alle&gt;</em>",
                    adGroupName: adGroupName,
                    adGroupDN: adGroupDN,
                    type: "system",
                    mapping: mapping
                });

                tableBody.append(row);
            });

            if (itSystemGroupFeatureRoleMap.length > 0) {
                this.loadRoleMappingsWithNames();
            } else {
                this.addListeners();
            }
        },

        loadRoleMappingsWithNames: function () {
            const tableBody = $("#mappingsTableBody");
            const rolePromises = [];

            itSystemGroupFeatureRoleMap.forEach(function (mapping) {
                const parts = mapping.split(";");
                const roleId = parts[0];
                const adGroupDN = parts[1];

                const rolePromise = Promise.resolve($.ajax({
                    url: userRoleBaseUrl + roleId,
                    method: "GET",
                    headers: {
                        "X-CSRF-TOKEN": token
                    }
                })).then(function (roleData) {
                    if (!roleData || !roleData.name || !roleData.itSystemName) {
                        throw new Error("Incomplete role data received for role ID: " + roleId);
                    }

                    return {
                        mapping: mapping,
                        roleId: roleId,
                        adGroupDN: adGroupDN,
                        roleName: roleData.name,
                        itSystemName: roleData.itSystemName,
                        itSystemId: roleData.itSystemId,
                        loaded: true
                    };
                }).catch(function (error) {
                    console.error("Failed to load role id=" + roleId + ":", error);
                    return {
                        mapping: mapping,
                        roleId: roleId,
                        adGroupDN: adGroupDN,
                        loaded: false
                    };
                });

                rolePromises.push(rolePromise);
            });

            Promise.all(rolePromises).then(function (roleDataArray) {
                let hasFailures = false;

                roleDataArray.forEach(function (roleData) {
                    const adGroupName = extractCNFromDN(roleData.adGroupDN);
                    let row;

                    if (roleData.loaded) {
                        row = settingService.createMappingRow({
                            itSystemName: roleData.itSystemName + " (id: " + roleData.itSystemId + ")",
                            roleName: roleData.roleName + " (id: " + roleData.roleId + ")",
                            adGroupName: adGroupName,
                            adGroupDN: roleData.adGroupDN,
                            type: "role",
                            mapping: roleData.mapping
                        });

                        if (!window.roleMappingToItSystem) {
                            window.roleMappingToItSystem = {};
                        }
                        if (roleData.itSystemId) {
                            window.roleMappingToItSystem[roleData.mapping] = roleData.itSystemId;
                        }
                    } else {
                        hasFailures = true;
                        row = settingService.createMappingRow({
                            itSystemName: "ukendt",
                            roleName: "<em>&lt;slettet rolle (id: " + roleData.roleId + ")&gt;</em>",
                            adGroupName: adGroupName,
                            adGroupDN: roleData.adGroupDN,
                            type: "role",
                            mapping: roleData.mapping
                        });
                    }

                    tableBody.append(row);
                });

                if (hasFailures) {
                    $.notify({
                        message: "Én eller flere jobfunktionsroller i mapningen kunne ikke findes. Slet de berørte rækker og gem.",
                        status: "error",
                        timeout: 5000
                    });
                }

                settingService.addListeners();
            });
        },

        createMappingRow: function (data) {
            const template = $("#itSystemGroupMappingRowTemplate").html();
            const $row = $(template);

            $row.find(".it-system-name").text(data.itSystemName);

            if (data.roleName.indexOf("<em>") !== -1) {
                $row.find(".role-name").html(data.roleName);
            } else {
                $row.find(".role-name").text(data.roleName);
            }

            $row.find(".ad-group-name")
                .text(data.adGroupName)
                .attr("title", data.adGroupDN);

            $row.find(".list-delete-btn")
                .attr("data-type", data.type)
                .attr("data-val", data.mapping);

            return $row;
        },

        initSystemAndUserRoleModalRoleSelect: function (itSystemId) {
            $("#systemAndUserRoleModalRole").val("");
            $("#systemAndUserRoleModalRole").select2({
                placeholder: "",
                allowClear: true,
                ajax: {
                    url: userRoleRestUrl + "?itsystem=" + itSystemId,
                    dataType: "json",
                    delay: 250,
                    data: function (params) {
                        return {
                            q: params.term,
                            page: params.page || 1
                        };
                    },
                    processResults: function (data, params) {
                        params.page = params.page || 1;
                        return {
                            results: data.results,
                            pagination: {
                                more: data.pagination
                            }
                        };
                    },
                    cache: true
                },
                minimumInputLength: 1,
                language: {
                    inputTooShort: function (args) {
                        const remaining = args.minimum - args.input.length;
                        return "Indtast venligst " + remaining + " eller flere tegn";
                    }
                }
            });
        },

        removeFromList: function (listId, valueToRemove, list) {
            if (listId === "") {
                const index = list.indexOf(valueToRemove);
                if (index > -1) {
                    list.splice(index, 1);
                }
                return;
            }

            $("#" + listId + " li").filter(function () {
                return $(this).find("span").text() === valueToRemove;
            }).remove();

            const index = list.indexOf(valueToRemove);
            if (index > -1) {
                list.splice(index, 1);
            }
        },

        addListeners: function () {
            $(".list-delete-btn").off("click").on("click", function () {
                const type = $(this).data("type");
                const value = $(this).data("val");

                switch (type) {
                    case "attribute":
                        settingService.removeFromList("", value, membershipSyncFeatureAttributeMap);
                        settingService.refreshAttributeMapTable();
                        break;
                    case "filter":
                        settingService.removeFromList("", value, membershipSyncFeatureFilterMap);
                        settingService.refreshFilterMapTable();
                        break;
                    case "backSyncOU":
                        settingService.removeFromList("", value, backSyncFeatureOUs);
                        settingService.refreshBackSyncOUTable();
                        break;
                    case "readonlySystem":
                        settingService.removeFromList("", value, readonlyItSystemFeatureSystemMap);
                        settingService.refreshReadonlySystemMapTable();
                        break;
                    case "system":
                        settingService.removeFromList("", value, itSystemGroupFeatureSystemMap);
                        settingService.updateMappingsTable();
                        break;
                    case "role":
                        settingService.removeFromList("", value, itSystemGroupFeatureRoleMap);
                        if (window.roleMappingToItSystem) {
                            delete window.roleMappingToItSystem[value];
                        }
                        settingService.updateMappingsTable();
                        break;
                }
            });

            $(".caretLink").off("click").on("click", function () {
                const icon = $(this).find(".caretIcon");
                icon.toggleClass("fa-caret-down");
                icon.toggleClass("fa-caret-right");
            });
        },

        createAttributeMapRow: function (data) {
            const template = document.getElementById("attributeMapRowTemplate");
            const clone = document.importNode(template.content, true);
            const $row = $(clone);

            const parts = data.split(";");
            $row.find(".group-name").text(parts[0] || "");
            $row.find(".attribute-name").text(parts[1] || "");
            $row.find(".attribute-value").text(parts[2] || "");
            $row.find(".list-delete-btn")
                .attr("data-type", "attribute")
                .attr("data-val", data);

            return $row;
        },

        createFilterMapRow: function (data) {
            const template = document.getElementById("filterMapRowTemplate");
            const clone = document.importNode(template.content, true);
            const $row = $(clone);

            const parts = data.split(";");
            $row.find(".group-name").text(parts[0] || "");
            $row.find(".attribute-name").text(parts[1] || "");
            $row.find(".filter-value").text(parts[2] || "");
            $row.find(".list-delete-btn")
                .attr("data-type", "filter")
                .attr("data-val", data);

            return $row;
        },

        createBackSyncOURow: function (data) {
            const template = document.getElementById("backSyncOURowTemplate");
            const clone = document.importNode(template.content, true);
            const $row = $(clone);

            const parts = data.split(";");
            const itSystemId = parts[0];
            const ouDN = parts[1];

            let itSystemText = $("#itSystemModalItSystem option[value='" + itSystemId + "']").text();
            if (!itSystemText || itSystemText === "") {
                itSystemText = "IT-system (ID: " + itSystemId + ")";
            }

            $row.find(".it-system-name").text(itSystemText);
            $row.find(".ou-dn").text(ouDN);
            $row.find(".list-delete-btn")
                .attr("data-type", "backSyncOU")
                .attr("data-val", data);

            return $row;
        },

        createReadonlySystemMapRow: function (data) {
            const template = document.getElementById("readonlySystemMapRowTemplate");
            const clone = document.importNode(template.content, true);
            const $row = $(clone);

            const parts = data.split(";");
            const itSystemId = parts[0];
            const groupDN = parts[1];

            let itSystemText = $("#itSystemModalItSystem option[value='" + itSystemId + "']").text();
            if (!itSystemText || itSystemText === "") {
                itSystemText = "IT-system (ID: " + itSystemId + ")";
            }

            $row.find(".it-system-name").text(itSystemText);
            $row.find(".group-dn").text(groupDN);
            $row.find(".list-delete-btn")
                .attr("data-type", "readonlySystem")
                .attr("data-val", data);

            return $row;
        },

        refreshAttributeMapTable: function () {
            const tableBody = $("#membershipSyncFeatureAttributeMapList");
            tableBody.empty();

            membershipSyncFeatureAttributeMap.forEach(function (item) {
                const row = settingService.createAttributeMapRow(item);
                tableBody.append(row);
            });

            this.addListeners();
        },

        refreshFilterMapTable: function () {
            const tableBody = $("#membershipSyncFeatureFilterMapList");
            tableBody.empty();

            membershipSyncFeatureFilterMap.forEach(function (item) {
                const row = settingService.createFilterMapRow(item);
                tableBody.append(row);
            });

            this.addListeners();
        },

        refreshBackSyncOUTable: function () {
            const tableBody = $("#backSyncFeatureOUsList");
            tableBody.empty();

            backSyncFeatureOUs.forEach(function (item) {
                const row = settingService.createBackSyncOURow(item);
                tableBody.append(row);
            });

            this.addListeners();
        },

        refreshReadonlySystemMapTable: function () {
            const tableBody = $("#readonlyItSystemFeatureSystemMapList");
            tableBody.empty();

            readonlyItSystemFeatureSystemMap.forEach(function (item) {
                const row = settingService.createReadonlySystemMapRow(item);
                tableBody.append(row);
            });

            this.addListeners();
        },

        save: function () {
            const payload = {
                createDeleteFeatureEnabled: $("#createDeleteFeatureEnabled").prop("checked"),
                createDeleteFeatureCreateEnabled: $("#createDeleteFeatureCreateEnabled").prop("checked"),
                createDeleteFeatureDeleteEnabled: $("#createDeleteFeatureDeleteEnabled").prop("checked"),
                createDeleteFeatureOU: $("#createDeleteFeatureOU").val(),
                createDeleteFeatureUseBackSyncOU: $("#createDeleteFeatureUseBackSyncOU").prop("checked"),
                membershipSyncFeatureEnabled: $("#membershipSyncFeatureEnabled").prop("checked"),
                membershipSyncFeatureIgnoreUsersWithoutCpr: $("#membershipSyncFeatureIgnoreUsersWithoutCpr").prop("checked"),
                membershipSyncFeatureCprAttribute: $("#membershipSyncFeatureCprAttribute").val(),
                membershipSyncFeatureAttributeMap: membershipSyncFeatureAttributeMap,
                membershipSyncFeatureFilterMap: membershipSyncFeatureFilterMap,
                membershipSyncFeatureDoNotRegisterDisabledUsers: $("#membershipSyncFeatureDoNotRegisterDisabledUsers").prop("checked"),
                fullMembershipSyncFeatureEnabled: $("#fullMembershipSyncFeatureEnabled").prop("checked"),
                backSyncFeatureEnabled: $("#backSyncFeatureEnabled").prop("checked"),
                backSyncFeatureGroupsInGroupOnSync: $("#backSyncFeatureGroupsInGroupOnSync").prop("checked"),
                backSyncFeatureMaintainDescriptionAndName: $("#backSyncFeatureMaintainDescriptionAndName").prop("checked"),
                backSyncFeatureCreateUserRoles: $("#backSyncFeatureCreateUserRoles").prop("checked"),
                backSyncFeatureOUs: backSyncFeatureOUs,
                backSyncFeatureNameAttribute: $("#backSyncFeatureNameAttribute").val(),
                itSystemGroupFeatureEnabled: $("#itSystemGroupFeatureEnabled").prop("checked"),
                itSystemGroupFeatureSystemMap: itSystemGroupFeatureSystemMap,
                itSystemGroupFeatureRoleMap: itSystemGroupFeatureRoleMap,
                itSystemGroupFeatureDoNotRegisterDisabledUsers: $("#itSystemGroupFeatureDoNotRegisterDisabledUsers").prop("checked"),
                readonlyItSystemFeatureEnabled: $("#readonlyItSystemFeatureEnabled").prop("checked"),
                readonlyItSystemFeatureSystemMap: readonlyItSystemFeatureSystemMap,
                readonlyItSystemFeatureNameAttribute: $("#readonlyItSystemFeatureNameAttribute").val(),
                logUploaderEnabled: $("#logUploaderEnabled").prop("checked"),
                logUploaderFileShareUrl: $("#logUploaderFileShareUrl").val(),
                logUploaderFileShareApiKey: $("#logUploaderFileShareApiKey").val(),
                sendErrorEmailFeatureEnabled: $("#sendErrorEmailFeatureEnabled").prop("checked"),
                sendingUserEmail: $("#sendingUserEmail").val(),
                recipientEmail: $("#recipientEmail").val(),
                tenantId: $("#tenantId").val(),
                clientId: $("#clientId").val(),
                clientSecret: $("#clientSecret").val(),
                includeNotesInDescription: $("#includeNotesInDescription").prop("checked")
            };

            swal({
                html: true,
                title: swalTitleTxt,
                text: swalBodyTxt,
                type: "warning",
                showCancelButton: true,
                confirmButtonColor: "#5d9cec",
                confirmButtonText: updateSettingsConfirmTxt,
                cancelButtonText: updateSettingsCancelTxt,
                closeOnConfirm: true,
                closeOnCancel: true
            },
            function (isConfirm) {
                if (isConfirm) {
                    $.ajax({
                        url: url + clientId,
                        method: "POST",
                        headers: {
                            "X-CSRF-TOKEN": token
                        },
                        contentType: "application/json",
                        data: JSON.stringify(payload),
                        error: function (response) {
                            errorHandler(updateFailedMsg)(response);
                        },
                        success: function () {
                            $.notify({
                                message: settingsUpdatedMsg,
                                status: "success",
                                timeout: 2000
                            });

                            window.location.reload();
                        }
                    });
                }
            });
        }
    };

    // ── Event bindings ──────────────────────────────────────────────────

    $(document).on("click", ".js-open-attribute-modal", function () {
        modalService.openSimpleModal("attribute");
    });

    $(document).on("click", ".js-open-filter-modal", function () {
        modalService.openSimpleModal("filter");
    });

    $(document).on("click", ".js-open-backsync-ou-modal", function () {
        modalService.openBackSyncFeatureOUsModal();
    });

    $(document).on("click", ".js-open-itsystem-role-modal", function () {
        modalService.openItSystemGroupFeatureSystemAndRoleMapModal();
    });

    $(document).on("click", ".js-open-readonly-system-modal", function () {
        modalService.openReadonlyItSystemFeatureSystemMapModal();
    });

    $(document).on("click", ".js-add-from-simple-modal", function () {
        modalService.addFromSimpleModal();
    });

    $(document).on("click", ".js-add-from-itsystem-modal", function () {
        modalService.addFromITSystemModal();
    });

    $(document).on("click", ".js-add-from-system-role-modal", function () {
        modalService.addFromSystemAndUserRoleModal();
    });

    $(document).on("click", ".js-save-settings", function () {
        settingService.save();
    });

    $(document).on("click", ".js-cancel", function () {
        window.location.reload();
    });

    // ── Initialize ──────────────────────────────────────────────────────

    settingService.init();
});
