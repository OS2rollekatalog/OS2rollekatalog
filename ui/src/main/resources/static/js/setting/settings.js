/**
 * Handles the settings/edit page: request-approve, case-number, and auto-niveau
 * checkboxes, the excluded-OUs jsTree picker, and the save notification.
 */
document.addEventListener("DOMContentLoaded", () => {
    const config = JSON.parse(document.getElementById("setting-config").textContent);
    const jsTreeService = new JsTreeService();

    initRequestApproveCheckbox();
    initCaseNumberCheckbox();
    initAutoNiveauCheckbox(config.autoNiveauEnabled);
    showSavedNotification(config.saved, config.successMsg, config.failedMsg);
    initOuTree(jsTreeService, config.allOUs, config.selectedOUs);
    initOuTreeSearch(jsTreeService);
});

document.addEventListener("click", (event) => {
    if (event.target.closest(".js-choose-ou-btn")) {
        $("#modal-ou").modal("show");
    }
    if (event.target.closest(".js-save-ous-btn")) {
        saveOUs();
    }
});

$(document).on("shown.bs.modal", "#modal-ou", () => {
    $("#ou-tree-search").focus();
});

function initRequestApproveCheckbox() {
    $("#requestapprove-checkbox").on("change", function () {
        $("#requestApproveEnabled").val(this.checked);

        if (this.checked) {
            $("#requestapprove-single-table-checkbox").prop("disabled", false);
        } else {
            $("#requestapprove-single-table-checkbox").prop("disabled", true).prop("checked", false);
            $("#showSingleTableInRequestApproveEnabled").val(false);
        }
    });
}

function initCaseNumberCheckbox() {
    $("#caseNumber-checkbox").on("change", function () {
        $("#caseNumberEnabled").val(this.checked);
    });
}

function initAutoNiveauCheckbox(autoNiveauEnabled) {
    $("#autoNiveau-checkbox").on("change", function () {
        $("#autoNiveauEnabled").val(this.checked);

        if (this.checked) {
            $("#level-mappings").show();
        } else {
            $("#level-mappings").hide();
        }
    });

    if (autoNiveauEnabled) {
        $("#level-mappings").show();
    } else {
        $("#level-mappings").hide();
    }
}

function showSavedNotification(saved, successMsg, failedMsg) {
    if (typeof saved === "undefined" || saved === null) {
        return;
    }

    if (saved) {
        $.notify({ message: successMsg }, { status: "success", autoHideDelay: 4000 });
    } else {
        $.notify({ message: failedMsg }, { status: "warning", autoHideDelay: 4000 });
    }
}

function initOuTree(jsTreeService, allOUs, selectedOUs) {
    jsTreeService.initTree("#ou-tree", {
        core: {
            data: allOUs,
            themes: {
                icons: false
            }
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

    $("#ou-tree").on("ready.jstree", () => {
        $("#ou-tree").jstree("select_node", selectedOUs);
    });
}

function initOuTreeSearch(jsTreeService) {
    let searchTimeout = null;

    $("#ou-tree-search").on("keyup", () => {
        if (searchTimeout) {
            clearTimeout(searchTimeout);
        }

        searchTimeout = setTimeout(() => {
            const searchValue = $("#ou-tree-search").val();
            jsTreeService.getInstance("#ou-tree").search(searchValue);
        }, 400);
    });
}

function saveOUs() {
    const codes = $("#ou-tree").jstree("get_selected", true);

    $("#excludedOUs").empty();

    codes.forEach((code) => {
        $("#excludedOUs").append(`<option value="${code.id}" selected="selected"> ${code.text}</option>`);
    });

    $("#filterZeroTxt").hide();
    $("#filterOneTxt").hide();
    $("#filterMultiTxt").hide();

    if (codes.length === 0) {
        $("#filterZeroTxt").show();
    } else if (codes.length === 1) {
        $("#filterOneTxt").show();
    } else {
        $("#filterMultiTxt").show();
        $("#filterMultiValue").text(codes.length);
    }

    $("#modal-ou").modal("hide");
}
