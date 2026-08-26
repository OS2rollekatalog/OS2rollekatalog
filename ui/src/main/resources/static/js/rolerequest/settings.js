async function handleFetchError(response) {
    const responseText = await response.text().catch(() => "");
    defaultErrorHandler({ status: response.status, responseText: responseText });
}

document.addEventListener("DOMContentLoaded", () => {
    const config = JSON.parse(document.getElementById("rolerequest-settings-config").textContent);
    const token = document.querySelector("meta[name='_csrf']").getAttribute("content");
    const constraintHandler = new ConstraintHandler(token, config.deleteURL, config.createURL);

    const deleteBtn = document.getElementById("deleteConstraintBtn");
    deleteBtn.addEventListener("click", () => constraintHandler.onConstraintDeletion());

    const constraintSelect = document.getElementById("constraintList");
    constraintSelect.addEventListener("change", () => constraintHandler.onConstraintChange(constraintSelect, deleteBtn));
    constraintHandler.onConstraintChange(constraintSelect, deleteBtn);

    const createBtn = document.getElementById("createConstraintBtn");
    createBtn.addEventListener("click", () => constraintHandler.onConstraintCreation());

    const createInputField = document.getElementById("constraintCreationInput");
    createInputField.addEventListener("change", () => constraintHandler.onCreateInputChange(createInputField, createBtn));
    constraintHandler.onCreateInputChange(createInputField, createBtn);

    $("#showRecommendedTabCheckbox").on("change", function () {
        $("#showRecommendedRolesTab").val(this.checked);
        enforceAtLeastOneTabSelected();
    });

    $("#showAllTabCheckbox").on("change", function () {
        $("#showAllRolesTab").val(this.checked);
        enforceAtLeastOneTabSelected();
    });

    $("#showExistingTabCheckbox").on("change", function () {
        $("#showExistingRolesTab").val(this.checked);
        enforceAtLeastOneTabSelected();
    });

    enforceAtLeastOneTabSelected();

    $("#single-table-checkbox").on("change", function () {
        $("#showSingleTableInRequestApproveEnabled").val(this.checked);
    });

    $("#allow-self-approval-checkbox").on("change", function () {
        $("#allowSelfApproval").val(this.checked);
    });

    $(".requesterSetting").on("change", function () {
        onRequesterCheckBoxClick(this);
    });

    $(".approverSetting").on("change", function () {
        onApproverCheckboxClick(this);
    });

    $(".checkbox-email").on("change", function () {
        const name = $(this).data("name");
        $("#approverEmail_" + name).prop("disabled", !$(this).is(":checked")).change();
        onEmailCheckboxClick(this);
    });

    $(".checkbox-email").trigger("change");

    // INIT code to disable if NONE/AUTOMATIC is pre-selected
    $(".requesterSetting:checked").each(function () {
        onRequesterCheckBoxClick(this);
    });
    $(".approverSetting:checked").each(function () {
        onApproverCheckboxClick(this);
        const id = $(this).data("id");
        if (id !== "AUTOMATIC") {
            $("#approver_" + id + "_email").show();
        }
    });
});

function enforceAtLeastOneTabSelected() {
    const checkboxes = [
        document.getElementById("showRecommendedTabCheckbox"),
        document.getElementById("showAllTabCheckbox"),
        document.getElementById("showExistingTabCheckbox")
    ];
    const checkedCount = checkboxes.filter((checkbox) => checkbox.checked).length;
    const saveButton = document.querySelector("#setting-form button[type='submit']");
    saveButton.disabled = checkedCount === 0;
}

function onApproverCheckboxClick(obj) {
    const id = $(obj).data("id");
    if (id === "AUTOMATIC") {
        if ($(obj).is(":checked")) {
            // disable all except AUTOMATIC
            $(".approverSetting").not(obj).prop("checked", false).prop("disabled", true);
            $(".inputEmailField").val("");
            $(".role-request-email").hide();
            $(".checkbox-email").prop("checked", false);
        } else {
            // re-enable all if AUTOMATIC is unchecked
            $(".approverSetting").prop("disabled", false);
        }
    } else {
        // If any other is clicked, make sure AUTOMATIC is unchecked & enabled
        $("#approver_AUTOMATIC").prop("checked", false).prop("disabled", false);
        const emailDiv = $("#approver_" + id + "_email");

        if ($(obj).is(":checked")) {
            emailDiv.show();
        } else {
            emailDiv.hide();
            emailDiv.find(".checkbox-email").prop("checked", false);
            emailDiv.find(".inputEmailField").val("");
        }
    }
}

function onEmailCheckboxClick(obj) {
    const emailField = $(obj).closest(".role-request-email").find(".inputEmailField");

    if ($(obj).is(":checked")) {
        emailField.prop("disabled", false).focus();
    } else {
        emailField.val("").prop("disabled", true);
    }
}

function onRequesterCheckBoxClick(obj) {
    const id = $(obj).data("id");
    if (id === "NONE") {
        if ($(obj).is(":checked")) {
            // disable all except NONE
            $(".requesterSetting").not(obj).prop("checked", false).prop("disabled", true);
        } else {
            // re-enable all if NONE is unchecked
            $(".requesterSetting").prop("disabled", false);
        }
    } else {
        // If any other is clicked, make sure NONE is unchecked & enabled
        $("#request_NONE").prop("checked", false).prop("disabled", false);
    }
}

class ConstraintHandler {
    constructor(token, deleteURL, createURL) {
        this.token = token;
        this.deleteURL = deleteURL;
        this.createURL = createURL;
        this.sweetAlertService = new SweetAlertService();
    }

    onCreateInputChange(createInputField, createBtn) {
        createBtn.disabled = createInputField.value.length === 0;
    }

    onConstraintChange(constraintSelect, deleteBtn) {
        const selected = Array.from(constraintSelect.options).filter((option) => option.selected);
        deleteBtn.disabled = selected.length < 1;
    }

    onConstraintDeletion() {
        const options = document.getElementById("constraintList").options;
        const selected = Array.from(options).filter((option) => option.selected);

        if (selected.length < 1) {
            return null;
        }

        const selectedValues = selected.map((selectedOption) => selectedOption.value);
        const selectedText = selected.map((selectedOption) => selectedOption.textContent);

        this.sweetAlertService.confirm(
            "Du er ved at slette følgende værdier:",
            `${selectedText.join(", ")}`,
            "Slet",
            "Fortryd",
            async () => {
                const response = await fetch(this.deleteURL + "?constraintIds=" + selectedValues.join(","), {
                    method: "DELETE",
                    headers: {
                        "X-CSRF-TOKEN": this.token
                    }
                });

                if (!response.ok) {
                    await handleFetchError(response);
                    return;
                }

                window.location.reload();
            }
        );
    }

    async onConstraintCreation() {
        const inputField = document.getElementById("constraintCreationInput");
        const value = inputField?.value;

        if (!value) {
            return;
        }

        const response = await fetch(this.createURL, {
            method: "POST",
            headers: {
                "X-CSRF-TOKEN": this.token,
                "Accept": "application/json",
                "Content-Type": "application/json"
            },
            body: JSON.stringify({
                value: value
            })
        });

        if (!response.ok) {
            await handleFetchError(response);
            return;
        }

        window.location.reload();
    }
}
