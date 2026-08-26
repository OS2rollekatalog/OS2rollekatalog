// Constraint editor for a user role's system roles: KLE/OU/function pickers,
// combo/freetext constraint value handling, and the postpone-checkbox logic.
//
// Delegated on document because the constraint rows are dynamically cloned
// into DataTables child rows (row.child()) rather than present in the DOM
// on page load.

let orgUnitConstraintService;
let functionConstraintService;

// Called explicitly from edit.js's DOMContentLoaded handler, after pageConfig
// and token are set but before showSelectedRoles() runs - showSelectedRoles
// depends on functionConstraintService/orgUnitConstraintService existing.
window.initConstraintEditor = function () {
    orgUnitConstraintService = new OrgUnitConstraintService(pageConfig);
    window.orgUnitConstraintService = orgUnitConstraintService;

    functionConstraintService = new FunctionConstraintService(pageConfig);
    window.functionConstraintService = functionConstraintService;

    orgUnitConstraintService.init();

    initJSTree("kle-tree", "kle-tree-search");

    $("#modal-kle").on("shown.bs.modal", () => {
        $("#kle-tree-search").focus();
    });

    initConstraintDelegation();
};

function initConstraintDelegation() {
    $(document).on("click", ".js-toggle-constraint", function () {
        toggleDisabled(this.dataset.systemroleid, this.dataset.constraintuuid, this.dataset.constrainttype);
    });

    $(document).on("click", ".js-choose-kles", function () {
        chooseKles(this.dataset.systemroleid, this.dataset.constraintuuid);
    });

    $(document).on("click", ".js-choose-ous", function () {
        orgUnitConstraintService.chooseOUs(this.dataset.systemroleid, this.dataset.constraintuuid);
    });

    $(document).on("click", ".js-choose-functions", function () {
        functionConstraintService.chooseFunctions(this.dataset.systemroleid, this.dataset.constraintuuid);
    });

    $(document).on("change", ".js-combo-constraint-select", function () {
        updateComboConstraintValue(this.dataset.systemroleid, this.dataset.constraintuuid, this.dataset.source);
    });

    $(document).on("change", ".js-value-constraint-input", function () {
        updateConstraintChoice(this.dataset.systemroleid, this.dataset.constraintuuid, "VALUE");
    });

    $(document).on("change", ".js-postpone-constraint", function () {
        saveConstraintChoice(this.dataset.systemroleid, this.dataset.constraintuuid, "POSTPONED", "", true);
    });

    $("#save.js-save-kle-constraints, .js-save-kle-constraints").on("click", kleModalSaveConstraints);
    $(".js-save-function-constraints").on("click", () => functionConstraintService.saveFunctions());

    // #modal-ou is shared between the regular constraint editor
    // (orgUnitConstraintService) and the postponed-constraints flow
    // (orgUnitPostponedConstraintService, from postponedConstraintsInit.js) —
    // the correct service is chosen based on which "choose OUs" entry point
    // opened the modal (each toggles which save button is visible).
    $("#modal-ou").on("click", ".js-save-postponed-ou-constraints", () => {
        window.orgUnitPostponedConstraintService.oUModalSaveConstraints();
    });
    $("#modal-ou").on("click", ".js-save-ou-constraints", () => {
        orgUnitConstraintService.oUModalSaveConstraints();
    });
    $("#modal-ou").on("click", ".js-save-ou-constraints-inherited", () => {
        orgUnitConstraintService.oUModalSaveConstraintsWithInheritance();
    });
}

function toggleDisabled(systemRoleId, constraintUuid, type) {
    const elem = (constraintUuid === pageConfig.itSystemConstraintUuid || type === "COMBO_MULTI")
        ? $(`#${systemRoleId}${constraintUuid}multiple-select`)
        : $(`#${systemRoleId}${constraintUuid}`);

    const postponeCheckbox = $(`#postpone${systemRoleId}${constraintUuid}`);

    if (postponeCheckbox.prop("checked") && elem.attr("disabled")) {
        postponeCheckbox.prop("checked", false);
        postponeCheckbox.attr("disabled", "disabled");

        elem.attr("disabled", "disabled");
        removeConstraintChoice(systemRoleId, constraintUuid); // disabling should remove the constraint
        elem.val("");
    } else if (elem.attr("disabled")) {
        postponeCheckbox.removeAttr("disabled");
        elem.removeAttr("disabled"); // enabling does not automatically set the value, the user must enter a value before we save anything
        if (constraintUuid === pageConfig.itSystemConstraintUuid || type === "COMBO_MULTI") {
            showFreetextAndFetchValue(systemRoleId, constraintUuid, type);
        }
    } else {
        postponeCheckbox.prop("checked", false);
        postponeCheckbox.attr("disabled", "disabled");

        elem.attr("disabled", "disabled");
        removeConstraintChoice(systemRoleId, constraintUuid); // disabling should remove the constraint
        elem.val("");
    }
}

function updateComboConstraintValue(systemRoleId, constraintUuid, constraintType) {
    const constraintValue = $(`#${systemRoleId}${constraintUuid}`).val();
    const legacyInheritedValues = [
        "INHERITED", "READ_AND_WRITE", "EXTENDED_INHERITED", "LEVEL_1", "LEVEL_2", "LEVEL_3",
        "LEVEL_4", "LEVEL_5", "LEVEL_6", "INHERITED_FROM_MANAGER_ROLE", "EXTENDED_INHERITED_FROM_MANAGER_ROLE"
    ];

    if (constraintValue === "INHERITED_FROM_FUNCTIONS" || constraintValue === "EXTENDED_INHERITED_FROM_FUNCTIONS") {
        hideFreetext(systemRoleId, constraintUuid);
        functionConstraintService.showFunctionPicker(systemRoleId, constraintUuid, constraintValue);
    } else if (legacyInheritedValues.includes(constraintValue)) {
        saveConstraintChoice(systemRoleId, constraintUuid, constraintValue, "", false);
        hideFreetext(systemRoleId, constraintUuid);
        functionConstraintService.hideFunctionPicker(systemRoleId, constraintUuid);
    } else if (constraintValue === "VALUE" && (constraintUuid === pageConfig.internalOuConstraintUuid || constraintUuid === pageConfig.ouConstraintUuid)) {
        functionConstraintService.hideFunctionPicker(systemRoleId, constraintUuid);
        orgUnitConstraintService.showOUAndFetchValue(constraintValue, systemRoleId, constraintUuid);
    } else if (constraintValue === "VALUE" && constraintUuid !== pageConfig.kleConstraintUuid && constraintUuid !== pageConfig.internalOuConstraintUuid && constraintUuid !== pageConfig.ouConstraintUuid) {
        functionConstraintService.hideFunctionPicker(systemRoleId, constraintUuid);
        showFreetextAndFetchValue(systemRoleId, constraintUuid, null);
    } else if (constraintValue === "VALUE" && constraintUuid === pageConfig.kleConstraintUuid) {
        functionConstraintService.hideFunctionPicker(systemRoleId, constraintUuid);
        showKleAndFetchValue(systemRoleId, constraintUuid);
    } else if (constraintValue === "SELECTED_INHERITED" && (constraintUuid === pageConfig.internalOuConstraintUuid || constraintUuid === pageConfig.ouConstraintUuid)) {
        functionConstraintService.hideFunctionPicker(systemRoleId, constraintUuid);
        orgUnitConstraintService.showOUAndFetchValue(constraintValue, systemRoleId, constraintUuid);
    }
}

function updateConstraintChoice(systemRoleId, constraintUuid, constraintValueType) {
    const constraintValue = $(`#${systemRoleId}${constraintUuid}`).val();
    saveConstraintChoice(systemRoleId, constraintUuid, constraintValueType, constraintValue, false);
}

function saveConstraintChoice(systemRoleId, constraintUuid, constraintValueType, constraintValue, fromPostpone) {
    const postpone = $(`#postpone${systemRoleId}${constraintUuid}`).prop("checked");
    const roleId = $("[name='id']").val();
    const newUrl = `${pageConfig.url}edit/${roleId}/addConstraint/${systemRoleId}`;

    $.ajax({
        url: newUrl,
        method: "POST",
        headers: {
            "X-CSRF-TOKEN": window.token
        },
        traditional: true, // required to deal with array issues
        data: {
            constraintUuid,
            constraintValue,
            constraintValueType,
            postpone
        },
        error: errorHandler(pageConfig.fieldNotUpdatedMsg),
        success: () => {
            window.notificationService.showInfoNotification(pageConfig.fieldUpdatedMsg);

            const elem = (constraintUuid === pageConfig.itSystemConstraintUuid)
                ? $(`#${systemRoleId}${constraintUuid}multiple-select`)
                : $(`#${systemRoleId}${constraintUuid}`);

            if (postpone) {
                elem.attr("disabled", "disabled");
                hideFreetext(systemRoleId, constraintUuid);
                $(`#${systemRoleId}${constraintUuid}freetext`).hide();
                elem.val("");
            } else if (fromPostpone && !postpone) {
                const constraintCheckbox = $(`#constraint-checkbox${systemRoleId}${constraintUuid}`);
                const postponeCheckbox = $(`#postpone${systemRoleId}${constraintUuid}`);
                postponeCheckbox.attr("disabled", "disabled");
                constraintCheckbox.prop("checked", false);

                elem.attr("disabled", "disabled");
                removeConstraintChoice(systemRoleId, constraintUuid); // disabling should remove the constraint
                elem.val("");
            }
        }
    });
}

function removeConstraintChoice(systemRoleId, constraintUuid) {
    const roleId = $("[name='id']").val();
    const newUrl = `${pageConfig.url}edit/${roleId}/removeConstraint/${systemRoleId}`;
    hideFreetext(systemRoleId, constraintUuid);

    $.ajax({
        url: newUrl,
        method: "POST",
        headers: {
            "X-CSRF-TOKEN": window.token
        },
        data: {
            constraintUuid
        },
        error: errorHandler(pageConfig.fieldNotUpdatedMsg),
        success: () => {
            window.notificationService.showInfoNotification(pageConfig.fieldUpdatedMsg);
        }
    });
}

function showOrHideFreetextAndFetchValue(systemRoleId, constraintUuid) {
    const constraintValueType = $(`#${systemRoleId}${constraintUuid}`).val();

    if (constraintValueType === "INHERITED_FROM_FUNCTIONS" || constraintValueType === "EXTENDED_INHERITED_FROM_FUNCTIONS") {
        hideFreetext(systemRoleId, constraintUuid);
        functionConstraintService.showFunctionPicker(systemRoleId, constraintUuid, constraintValueType);
        return;
    }

    if (constraintValueType === "VALUE") {
        functionConstraintService.hideFunctionPicker(systemRoleId, constraintUuid);
        if (constraintUuid === pageConfig.kleConstraintUuid) {
            showKleAndFetchValue(systemRoleId, constraintUuid);
            return;
        }
        if (constraintUuid === pageConfig.ouConstraintUuid || constraintUuid === pageConfig.internalOuConstraintUuid) {
            orgUnitConstraintService.showOUAndFetchValue(constraintValueType, systemRoleId, constraintUuid);
            return;
        }
        showFreetextAndFetchValue(systemRoleId, constraintUuid, null);
        return;
    }

    if (constraintValueType === "SELECTED_INHERITED" && (pageConfig.internalOuConstraintUuid === constraintUuid || pageConfig.ouConstraintUuid === constraintUuid)) {
        functionConstraintService.hideFunctionPicker(systemRoleId, constraintUuid);
        orgUnitConstraintService.showOUAndFetchValue("SELECTED_INHERITED", systemRoleId, constraintUuid);
        return;
    }

    functionConstraintService.hideFunctionPicker(systemRoleId, constraintUuid);
    hideFreetext(systemRoleId, constraintUuid);
}

function showKleAndFetchValue(systemRoleId, constraintUuid) {
    $(`#${systemRoleId}${constraintUuid}freetext`).show();

    const kleInput = $(`#${systemRoleId}${constraintUuid}input`);
    kleInput.val($(`#${systemRoleId}${constraintUuid}values`).val().replace(/\.\*/g, ""));

    kleInput.off("change");
    kleInput.on("change", () => {
        const constraintValueType = $(`#${systemRoleId}${constraintUuid}`).val();
        const selected = kleInput.val().split(",");

        const constraintValue = selected.map((value) => (
            (value.length !== 8 && value !== "*") ? `${value}.*` : value
        ));

        saveConstraintChoice(systemRoleId, constraintUuid, constraintValueType, constraintValue, false);
    });
}

function showFreetextAndFetchValue(systemRoleId, constraintUuid, type) {
    const multipleSelectField = $(`#${systemRoleId}${constraintUuid}multiple-select`);

    if (constraintUuid === pageConfig.ouConstraintUuid || constraintUuid === pageConfig.internalOuConstraintUuid) {
        let constraintVals = [];

        pageConfig.selectedItSystems.forEach((element) => {
            const existing = element.selectedConstraints[constraintUuid];
            if (existing !== undefined) {
                constraintVals = existing.constraintValue.split(",");
            }
        });

        // NOTE: original code used `orgUnitList.array.forEach(...)`, which
        // relies on an `.array` property that likely doesn't exist on
        // orgUnitList. Corrected to iterate orgUnitList directly - please
        // confirm this matches the intended data shape.
        pageConfig.orgUnitList.forEach((element) => {
            const newOption = document.createElement("option");
            newOption.value = element.id;
            newOption.text = element.text;

            if (constraintVals.includes(element.id)) {
                newOption.selected = "selected";
            }

            multipleSelectField.append(newOption);
        });

        const select = document.getElementById(`${systemRoleId}${constraintUuid}multiple-select`);
        for (const option of select.options) {
            if (constraintVals.includes(option.value)) {
                option.selected = "selected";
            }
        }
    } else if (constraintUuid === pageConfig.itSystemConstraintUuid) {
        let constraintVals = [];

        pageConfig.selectedItSystems.forEach((element) => {
            if (element.id.toString() === systemRoleId) {
                const existing = element.selectedConstraints[constraintUuid];
                if (existing !== undefined) {
                    constraintVals = existing.constraintValue.split(",");
                }
            }
        });

        pageConfig.itSystemList.forEach((element) => {
            const newOption = document.createElement("option");
            newOption.value = element.id;
            newOption.text = element.text;

            if (constraintVals.includes(element.id)) {
                newOption.selected = "selected";
            }

            multipleSelectField.append(newOption);
        });

        const select = document.getElementById(`${systemRoleId}${constraintUuid}multiple-select`);
        for (const option of select.options) {
            if (constraintVals.includes(option.value)) {
                option.selected = "selected";
            }
        }

        multipleSelectField.select2();
    }

    if (type === "COMBO_MULTI") {
        multipleSelectField.select2();
    }

    multipleSelectField.show();
    multipleSelectField.trigger("change");

    $(`#${systemRoleId}${constraintUuid}freetext`).show();

    multipleSelectField.off("change.custom");
    multipleSelectField.on("change.custom", () => {
        const constraintValueType = $(`#${systemRoleId}${constraintUuid}`).val();
        const constraintValue = (multipleSelectField.val() !== null) ? multipleSelectField.val().join(",") : "";

        saveConstraintChoice(systemRoleId, constraintUuid, constraintValueType, constraintValue, false);
    });
}

function hideFreetext(systemRoleId, constraintType) {
    $(`#${systemRoleId}${constraintType}multiple-select`).attr("disabled", "disabled");
    $(`#${systemRoleId}${constraintType}freetext`).hide();
}

function enableFreetextAndFetchValue(systemRoleId, constraintUuid, type) {
    const multipleSelectField = $(`#${systemRoleId}${constraintUuid}multiple-select`);

    if (multipleSelectField.length) {
        showFreetextAndFetchValue(systemRoleId, constraintUuid, type);
    }
}

// --- KLE modal ---

function chooseKles(systemRoleId, constraintUuid) {
    const kles = $(`#${systemRoleId}${constraintUuid}input`).val().split(",");

    $("#modal-systemRoleId").val(systemRoleId);
    $("#modal-constraintUuid").val(constraintUuid);

    const selected = [];
    const parseErrors = [];

    kles.forEach((kle) => {
        if (kle !== "") {
            if (kle.match(/^((\d{2})|(\d{2}).(\d{2})|(\d{2}).(\d{2}).(\d{2}))$/)) {
                selected.push(kle);
            } else {
                parseErrors.push(kle);
            }
        }
    });

    const errorList = $("#modal-errors");
    errorList.empty();
    if (parseErrors.length !== 0) {
        parseErrors.forEach((error) => {
            errorList.append(`<li>${error}</li>`);
        });
        $("#modal-error").show();
    } else {
        $("#modal-error").hide();
    }

    const tree = $("#kle-tree");
    tree.jstree("deselect_all");
    tree.jstree("select_node", selected);

    $("#modal-kle").modal("show");
}

function kleModalSaveConstraints() {
    const systemRoleId = $("#modal-systemRoleId").val();
    const constraintUuid = $("#modal-constraintUuid").val();
    const constraintValueType = $(`#${systemRoleId}${constraintUuid}`).val();
    const kleInput = $(`#${systemRoleId}${constraintUuid}input`);

    const selected = $("#kle-tree").jstree("get_top_checked");
    kleInput.val(selected.join());
    $("#modal-kle").modal("hide");
    kleInput.trigger("change");
}

function initJSTree(id, search) {
    window.jsTreeService.initTree(`#${id}`, {
        core: {
            data: pageConfig.kleList
        },
        checkbox: {
            keep_selected_style: false,
            three_state: false,
            cascade: "undetermined"
        },
        search: {
            show_only_matches: true,
            search_callback: (str, node) => {
                // Special KLE search support
                let kleValue = str.split(".").join("");
                if (!isNaN(kleValue)) {
                    if (kleValue.length > 4) {
                        kleValue = `${kleValue.substr(0, 2)}.${kleValue.substr(2, 2)}.${kleValue.substr(4)}`;
                    } else if (kleValue.length > 2) {
                        kleValue = `${kleValue.substr(0, 2)}.${kleValue.substr(2)}`;
                    }

                    return node.text.startsWith(kleValue);
                }

                return node.text.toUpperCase().includes(str.toUpperCase());
            }
        },
        plugins: ["wholerow", "search", "checkbox"]
    });

    let searchTimeout = false;
    $(`#${search}`).on("keyup", () => {
        if (searchTimeout) {
            clearTimeout(searchTimeout);
        }

        searchTimeout = setTimeout(() => {
            const searchValue = $(`#${search}`).val();
            window.jsTreeService.getInstance(`#${id}`).search(searchValue);
        }, 400);
    });
}

// --- Function constraint picker/modal ---

class FunctionConstraintService {
    constructor(config) {
        this.config = config;
        this.currentSystemRoleId = null;
        this.currentConstraintUuid = null;
        this.currentConstraintValueType = null;
    }

    #updateSaveButtonState() {
        const checkedCount = $("#modal-function-checkbox-list input[type=checkbox]:checked").length;
        $("#modal-function-warning").toggle(checkedCount === 0);
    }

    showFunctionPicker(systemRoleId, constraintUuid, constraintValueType) {
        this.currentSystemRoleId = systemRoleId;
        this.currentConstraintUuid = constraintUuid;
        this.currentConstraintValueType = constraintValueType;

        const pickerDiv = $(`#${systemRoleId}${constraintUuid}funcpicker`);
        pickerDiv.show();

        const saved = $(`#${systemRoleId}${constraintUuid}funcvalues`).val();
        const selectedUuids = saved ? saved.split(",").filter((value) => value.trim() !== "") : [];
        const label = this.buildLabel(selectedUuids);
        $(`#${systemRoleId}${constraintUuid}funcinput`).val(label);
    }

    hideFunctionPicker(systemRoleId, constraintUuid) {
        $(`#${systemRoleId}${constraintUuid}funcpicker`).hide();
    }

    buildLabel(selectedUuids) {
        if (!selectedUuids || selectedUuids.length === 0) {
            return "";
        }
        const names = selectedUuids.map((uuid) => {
            const fn = this.config.allFunctions.find((f) => f.uuid === uuid);
            return fn ? fn.name : uuid;
        });
        return names.join(", ");
    }

    chooseFunctions(systemRoleId, constraintUuid) {
        this.currentSystemRoleId = systemRoleId;
        this.currentConstraintUuid = constraintUuid;
        this.currentConstraintValueType = $(`#${systemRoleId}${constraintUuid}`).val();

        const saved = $(`#${systemRoleId}${constraintUuid}funcvalues`).val();
        const selectedUuids = saved ? saved.split(",").filter((value) => value.trim() !== "") : [];

        const tbody = $("#modal-function-checkbox-list");
        tbody.empty();
        this.config.allFunctions.forEach((fn) => {
            const checked = selectedUuids.includes(fn.uuid) ? "checked" : "";
            tbody.append(
                `<tr><td><input type="checkbox" value="${fn.uuid}" ${checked}></td>` +
                `<td>${$("<span>").text(fn.name).html()}</td></tr>`
            );
        });

        const selectAll = $("#modal-function-select-all");
        selectAll.prop("checked", selectedUuids.length === this.config.allFunctions.length && this.config.allFunctions.length > 0);
        selectAll.off("change").on("change", () => {
            tbody.find("input[type=checkbox]").prop("checked", selectAll.prop("checked"));
            this.#updateSaveButtonState();
        });
        tbody.off("change", "input").on("change", "input", () => {
            const total = tbody.find("input[type=checkbox]").length;
            const checked = tbody.find("input[type=checkbox]:checked").length;
            selectAll.prop("checked", total > 0 && total === checked);
            this.#updateSaveButtonState();
        });
        this.#updateSaveButtonState();

        const searchField = $("#modal-function-search");
        searchField.val("").off("input").on("input", () => {
            const query = searchField.val().toLowerCase();
            tbody.find("tr").each(function () {
                $(this).toggle($(this).find("td:last").text().toLowerCase().indexOf(query) >= 0);
            });
        });

        $("#modal-function").modal("show");
    }

    saveFunctions() {
        const selected = [];
        $("#modal-function-checkbox-list input:checked").each(function () {
            selected.push($(this).val());
        });

        const { currentSystemRoleId, currentConstraintUuid, currentConstraintValueType } = this;

        $(`#${currentSystemRoleId}${currentConstraintUuid}funcvalues`).val(selected.join(","));
        $(`#${currentSystemRoleId}${currentConstraintUuid}funcinput`).val(this.buildLabel(selected));

        saveConstraintChoice(currentSystemRoleId, currentConstraintUuid, currentConstraintValueType, selected, false);
        $("#modal-function").modal("hide");
    }
}

// --- OU constraint picker/modal ---

class OrgUnitConstraintService {
    constructor(config) {
        this.config = config;
    }

    init() {
        $("#modal-ou").on("shown.bs.modal", () => {
            $("#ou-tree-search").focus();
        });

        this.initOUJSTree("ou-tree", "ou-tree-search", this.config.treeOUs);
    }

    showOUAndFetchValue(constraintValue, systemRoleId, constraintUuid) {
        const ouInput = $(`#${systemRoleId}${constraintUuid}input`);
        const freetextDiv = $(`#${systemRoleId}${constraintUuid}freetext`);
        const valuesInput = $(`#${systemRoleId}${constraintUuid}values`);

        // Set the value directly from the hidden values input - no tree manipulation.
        if (valuesInput.length > 0 && valuesInput.val()) {
            ouInput.val(valuesInput.val());
        }

        freetextDiv.show();

        ouInput.off("change");
        ouInput.on("change", () => {
            const constraintValueType = $(`#${systemRoleId}${constraintUuid}`).val();
            const selected = ouInput.val().split(",").filter((value) => value);

            saveConstraintChoice(systemRoleId, constraintUuid, constraintValueType, selected, false);
        });
    }

    chooseOUs(systemRoleId, constraintUuid) {
        const constraintValueType = $(`#${systemRoleId}${constraintUuid}`).val();
        const tree = $("#ou-tree");
        tree.jstree("deselect_all");

        const ousValue = $(`#${systemRoleId}${constraintUuid}input`).val() || "";
        const ous = ousValue.split(",").filter((ou) => ou && ou.trim() !== "");

        $("#modal-ou-systemRoleId").val(systemRoleId);
        $("#modal-ou-constraintUuid").val(constraintUuid);

        if (constraintValueType !== "SELECTED_INHERITED") {
            const selected = [];
            const parseErrors = [];

            ous.forEach((ou) => {
                const isValidUuid = /^[0-9a-fA-F]{8}\b-[0-9a-fA-F]{4}\b-[0-9a-fA-F]{4}\b-[0-9a-fA-F]{4}\b-[0-9a-fA-F]{12}$/.test(ou);
                if (isValidUuid) {
                    selected.push(ou);
                } else {
                    parseErrors.push(ou);
                }
            });

            const errorList = $("#modal-ou-errors");
            errorList.empty();
            if (parseErrors.length !== 0) {
                // NOTE: original loop used `for (const i = 0; ...; i++)`, which
                // throws "Assignment to constant variable" - fixed to `let`.
                for (let i = 0; i < parseErrors.length; i++) {
                    errorList.append(`<li>${parseErrors[i]}</li>`);
                }
                $("#modal-ou-error").show();
            } else {
                $("#modal-ou-error").hide();
            }

            tree.jstree("destroy").empty();
            this.initOUJSTree("ou-tree", "ou-tree-search", this.config.treeOUs);

            tree.on("ready.jstree", () => {
                tree.jstree("select_node", selected);
            });
        } else {
            tree.jstree("destroy").empty();
            this.initOUJSTree("ou-tree", "ou-tree-search", this.config.treeOUs, "none");

            setTimeout(() => {
                const treeInstance = tree.jstree(true);

                const parents = [];
                const excludedOus = [];
                ous.forEach((ou) => {
                    if (!ou) {
                        return;
                    }
                    if (ou.startsWith("+")) {
                        parents.push(ou.substring(1));
                    } else if (ou.startsWith("-")) {
                        excludedOus.push(ou.substring(1));
                    }
                });

                const nodesToSelect = [];
                parents.forEach((parentId) => {
                    const parentNode = treeInstance.get_node(parentId);
                    if (parentNode) {
                        nodesToSelect.push(parentId);

                        if (parentNode.children_d && parentNode.children_d.length > 0) {
                            parentNode.children_d.forEach((childId) => {
                                if (!excludedOus.includes(childId)) {
                                    nodesToSelect.push(childId);
                                }
                            });
                        }
                    }
                });

                const selectedSet = new Set(nodesToSelect);
                const treeData = JSON.parse(JSON.stringify(this.config.treeOUs)); // Deep clone

                const markNodesInData = (nodes) => {
                    if (!Array.isArray(nodes)) {
                        return;
                    }
                    nodes.forEach((node) => {
                        node.state = node.state || {};
                        const isSelected = selectedSet.has(node.id);
                        node.state.selected = isSelected;
                        node.state.checked = isSelected;

                        if (node.children && node.children.length > 0) {
                            markNodesInData(node.children);
                        }
                    });
                };

                markNodesInData(treeData);

                tree.jstree("destroy").empty();
                this.initOUJSTree("ou-tree", "ou-tree-search", treeData, "none");

                tree.on("ready.jstree", () => {
                    setTimeout(() => {
                        const readyTreeInstance = tree.jstree(true);
                        readyTreeInstance.settings.checkbox.cascade = "undetermined";
                        readyTreeInstance.settings.checkbox.three_state = true;

                        tree.off("changed.jstree");
                        tree.on("changed.jstree", (event, data) => {
                            const treeInst = tree.jstree(true);
                            if (data.action === "select_node") {
                                data.node.children.forEach((child) => treeInst.select_node(child));
                            } else if (data.action === "deselect_node") {
                                data.node.children.forEach((child) => treeInst.deselect_node(child));
                            }
                        });
                    }, 200);
                });
            }, 100);
        }

        $(".postponed-constraint-save-btn").hide();
        $(".constraint-save-btn").show();

        $("#modal-ou").modal("show");
        this.hideButtonsPerContext(constraintValueType);
    }

    hideButtonsPerContext(constraintValueType) {
        if (constraintValueType !== "SELECTED_INHERITED") {
            $("#ou-modal-save-inherited").hide();
        } else {
            $("#ou-modal-save-default").hide();
        }
    }

    oUModalSaveConstraintsWithInheritance() {
        const systemRoleId = $("#modal-ou-systemRoleId").val();
        const constraintUuid = $("#modal-ou-constraintUuid").val();
        const ouInput = $(`#${systemRoleId}${constraintUuid}input`);
        const tree = $("#ou-tree").jstree(true);

        const selectedIds = tree.get_selected();
        const selectedNodes = selectedIds.map((id) => tree.get_node(id));
        const selectedSet = new Set(selectedIds);
        const resultSet = new Set();
        const processedNodes = new Set();

        const topLevelParents = selectedNodes.filter((node) => (
            node.children.length > 0 && !this.hasCheckedAncestor(node, selectedSet, tree)
        ));

        topLevelParents.forEach((parent) => {
            const allDescendants = parent.children_d || [];
            const unselectedDescendants = allDescendants.filter((id) => !selectedSet.has(id));

            resultSet.add(`+${parent.id}`);
            unselectedDescendants.forEach((id) => resultSet.add(`-${id}`));

            processedNodes.add(parent.id);
            allDescendants.forEach((id) => processedNodes.add(id));
        });

        selectedNodes.forEach((node) => {
            if (!processedNodes.has(node.id) && node.children.length === 0) {
                resultSet.add(node.id);
            }
        });

        ouInput.val(Array.from(resultSet).join(","));
        $("#modal-ou").modal("hide");
        ouInput.trigger("change");
    }

    allChildrenChecked(node, selectedSet, treeInstance) {
        return node.children.every((childId) => {
            if (!selectedSet.has(childId)) {
                return false;
            }
            const childNode = treeInstance.get_node(childId);
            return this.allChildrenChecked(childNode, selectedSet, treeInstance);
        });
    }

    hasCheckedAncestor(node, selectedSet, treeInstance) {
        let parent = treeInstance.get_node(node.parent);
        while (parent && parent.id !== "#") {
            if (selectedSet.has(parent.id)) {
                return true;
            }
            parent = treeInstance.get_node(parent.parent);
        }
        return false;
    }

    getUncheckedDescendants(node, selectedSet, treeInstance) {
        let unchecked = [];
        node.children.forEach((childId) => {
            if (!selectedSet.has(childId)) {
                unchecked.push(childId);
            }
            const childNode = treeInstance.get_node(childId);
            unchecked = unchecked.concat(this.getUncheckedDescendants(childNode, selectedSet, treeInstance));
        });
        return unchecked;
    }

    expandInheritedToExplicit(rawValues, treeInstance) {
        const result = new Set();
        rawValues.split(",").forEach((value) => {
            if (value.startsWith("+")) {
                const node = treeInstance.get_node(value.slice(1));
                if (node) {
                    node.children_d.forEach((id) => result.add(id));
                }
            } else if (!value.startsWith("-") && value.trim() !== "") {
                result.add(value);
            }
        });
        return [...result];
    }

    convertFlatToInherited(flatList, treeInstance) {
        const selectedSet = new Set(flatList);
        const result = new Set();

        const rootNodes = treeInstance.get_json("#", { flat: false });
        if (rootNodes.length === 1) {
            const root = treeInstance.get_node(rootNodes[0].id);
            if (root.children_d.every((id) => selectedSet.has(id))) {
                return [`+${root.id}`];
            }
        }

        selectedSet.forEach((id) => {
            const node = treeInstance.get_node(id);
            if (!node || this.hasCheckedAncestor(node, selectedSet, treeInstance)) {
                return;
            }

            if (node.children.length > 0) {
                result.add(`+${node.id}`);
                if (!this.allChildrenChecked(node, selectedSet, treeInstance)) {
                    this.getUncheckedDescendants(node, selectedSet, treeInstance).forEach((uncheckedId) => {
                        result.add(`-${uncheckedId}`);
                    });
                }
            } else {
                result.add(node.id);
            }
        });

        return Array.from(result);
    }

    oUModalSaveConstraints() {
        const systemRoleId = $("#modal-ou-systemRoleId").val();
        const constraintUuid = $("#modal-ou-constraintUuid").val();
        const ouInput = $(`#${systemRoleId}${constraintUuid}input`);

        const selected = $("#ou-tree").jstree("get_checked");
        ouInput.val(selected.join());
        $("#modal-ou").modal("hide");
        ouInput.trigger("change");
    }

    initOUJSTree(id, search, list, mode = "undetermined") {
        window.jsTreeService.initTree(`#${id}`, {
            core: {
                data: list
            },
            checkbox: {
                keep_selected_style: false,
                three_state: false,
                cascade: mode
            },
            search: {
                show_only_matches: true,
                search_callback: (str, node) => node.text.toUpperCase().includes(str.toUpperCase())
            },
            plugins: ["wholerow", "search", "checkbox"]
        });

        let searchTimeout = false;
        $(`#${search}`).on("keyup", () => {
            if (searchTimeout) {
                clearTimeout(searchTimeout);
            }

            searchTimeout = setTimeout(() => {
                const searchValue = $(`#${search}`).val();
                window.jsTreeService.getInstance(`#${id}`).search(searchValue);
            }, 400);
        });
    }
}
