/**
 * Handles the attestation settings page: scheduled-attestation checkboxes,
 * the opt-in/opt-out OU picker (sharing the choose_ou_modal fragment), and
 * the first-attestation-date picker.
 */
let excemptedOusState = [];
let optedInOusState = [];
let optIn = false;
let allOUs = [];

document.addEventListener("DOMContentLoaded", () => {
    const config = JSON.parse(document.getElementById("attestation-setting-config").textContent);
    const jsTreeService = new JsTreeService();
    const datePickerService = new DatePickerService();

    excemptedOusState = config.selectedOUs;
    optedInOusState = config.optedInOuSelection;
    optIn = config.optIn;
    allOUs = config.allOUs;

    initOptInCheckbox();
    initScheduledAttestationCheckbox();
    initDontSendMailToManagerCheckbox();
    initAdAttestationCheckbox();
    initAttestationChangesCheckbox();
    initDescriptionRequiredCheckbox();
    initHideDescriptionCheckbox();
    showSavedNotification(config.saved, config.successMsg, config.failedMsg);
    initOuTree(jsTreeService);
    initOuTreeSearch(jsTreeService);
    initAttestationDatePicker(datePickerService, config.firstAttestationDate);
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

function initOptInCheckbox() {
    const optInCheckElement = document.getElementById("ou-optin-checkbox");
    optInCheckElement.addEventListener("click", () => {
        onOptInCheckboxChange(optInCheckElement);
    });
}

function initScheduledAttestationCheckbox() {
    $("#scheduled-attestation-checkbox").on("change", function () {
        $("#scheduledAttestationEnabled").val(this.checked);

        if (this.checked) {
            $("#scheduledAttestationSubsettings").show();
        } else {
            $("#scheduledAttestationSubsettings").hide();
        }
    });

    if ($("#scheduledAttestationEnabled").val() === "true") {
        $("#scheduledAttestationSubsettings").show();
    }
}

function initDontSendMailToManagerCheckbox() {
    $("#dont-send-mail-to-manager-checkbox").on("change", function () {
        $("#dontSendMailToManager").val(this.checked);
    });
}

function initAdAttestationCheckbox() {
    $("#attestation-ad-checkbox").on("change", function () {
        $("#adAttestationEnabled").val(this.checked);
    });
}

function initAttestationChangesCheckbox() {
    $("#attestation-changes-checkbox").on("change", function () {
        $("#changeRequestsEnabled").val(this.checked);
    });
}

function initDescriptionRequiredCheckbox() {
    $("#description-required-checkbox").on("change", function () {
        $("#descriptionRequired").val(this.checked);

        $("#hide-description-checkbox").prop("checked", false);
        $("#hideDescription").val(false);

        if (this.checked) {
            $("#hideDescriptionSection").hide();
        } else {
            $("#hideDescriptionSection").show();
        }
    });

    if ($("#description-required-checkbox").prop("checked")) {
        $("#hideDescriptionSection").hide();
    } else {
        $("#hideDescriptionSection").show();
    }
}

function initHideDescriptionCheckbox() {
    $("#hide-description-checkbox").on("change", function () {
        $("#hideDescription").val(this.checked);
    });
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

function initOuTree(jsTreeService) {
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
        if (optIn) {
            recoverOptinTreeState();
        } else {
            recoverExcemptedTreeState();
        }
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

function initAttestationDatePicker(datePickerService, firstAttestationDate) {
    datePickerService.setDate("attestationStartDate", new Date(firstAttestationDate));

    $("#attestationStartDate").on("dp.change", () => {
        if (!$("#attestationStartDate").data("date")) {
            datePickerService.setDate("attestationStartDate", firstAttestationDate);
        }

        $("#firstAttestationDate").val($("#attestationStartDate").data("date"));
    });
}

/**
 * Called from the choose_ou_modal fragment's save button (js-save-ous-btn).
 */
function saveOUs() {
    if (optIn) {
        setOptinTreeStateFromSelection();
        createSelectedOptionsFor("#scheduledAttestationOptedInOrgUnits", optedInOusState);
    } else {
        setExcemptedTreeStateFromSelection();
        createSelectedOptionsFor("#scheduledAttestationFilter", excemptedOusState);
    }

    setOrgunitSelectionTextMessages();
    hideOUModal();
}

function createSelectedOptionsFor(elementSelector, idArray) {
    const targetElement = $(elementSelector);
    targetElement.empty();

    // NOTE: preserved as-is from the original inline script — the trailing
    // quote after </option> is an existing quirk, not new behavior.
    const selectedOusAsOptions = idArray.map((id) => `<option value="${id}" selected="selected"></option>"`);
    targetElement.append(selectedOusAsOptions);
}

function setOrgunitSelectionTextMessages() {
    let length = excemptedOusState.length;
    if (optIn) {
        length = optedInOusState.length;
    }

    $("#filterZeroTxt").hide();
    $("#filterOneTxt").hide();
    $("#filterMultiTxt").hide();
    $("#optInZeroTxt").hide();
    $("#optInOneTxt").hide();
    $("#optInMultiTxt").hide();

    if (length === 0) {
        $("#filterZeroTxt").show();
        $("#optInZeroTxt").show();
    } else if (length === 1) {
        $("#filterOneTxt").show();
        $("#optInOneTxt").show();
    } else {
        $("#filterMultiTxt").show();
        $("#optInMultiTxt").show();
        $("#filterMultiValue").text(length);
        $("#optInMultiValue").text(length);
    }
}

function hideOUModal() {
    $("#modal-ou").modal("hide");
}

function recoverOptinTreeState() {
    $("#ou-tree").jstree("deselect_node", allOUs);
    $("#ou-tree").jstree("select_node", optedInOusState);
}

function recoverExcemptedTreeState() {
    $("#ou-tree").jstree("deselect_node", allOUs);
    $("#ou-tree").jstree("select_node", excemptedOusState);
}

function setOptinTreeStateFromSelection() {
    optedInOusState = $("#ou-tree").jstree("get_selected");
}

function setExcemptedTreeStateFromSelection() {
    excemptedOusState = $("#ou-tree").jstree("get_selected");
}

function onOptInCheckboxChange(optInCheckElement) {
    const checked = optInCheckElement.checked;
    const optInSectionElement = document.getElementById("opt-in-section");
    const optOutSectionElement = document.getElementById("opt-out-section");

    if (checked) {
        optIn = true;
        optInSectionElement.style.display = "flex";
        optOutSectionElement.style.display = "none";

        recoverOptinTreeState();
        setOrgunitSelectionTextMessages();
    } else {
        optIn = false;
        optInSectionElement.style.display = "none";
        optOutSectionElement.style.display = "flex";

        recoverExcemptedTreeState();
        setOrgunitSelectionTextMessages();
    }
}
