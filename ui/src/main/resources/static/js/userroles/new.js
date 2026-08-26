// New user role form: toggles the custom-identifier field based on the
// selected IT system's type, and toggles the identifier input's visibility
// based on its own checkbox.

document.addEventListener("DOMContentLoaded", () => {
    // NOTE: config.identifier is parsed but not currently used anywhere in
    // this script — preserved from the original inline script as-is.
    const config = JSON.parse(document.getElementById("userrole-new-config").textContent);

    $("[rel=popover]").popover();

    const identifierService = new IdentifierService();
    identifierService.addChangeListeners();
    identifierService.selectChange();
});

/**
 * Toggles the "custom identifier" checkbox row and input row based on the
 * selected IT system's type (AD/NEMLOGIN systems never allow a custom
 * identifier).
 */
class IdentifierService {
    addChangeListeners() {
        $("#itSystem").on("change", this.selectChange);
        $("#checkbox").on("change", this.checkboxChange);
    }

    selectChange() {
        const type = $("#itSystem").find(":selected").attr("data-type");

        if (type !== "AD" && type !== "NEMLOGIN") {
            $("#checkboxRow").attr("hidden", false);
        } else {
            $("#checkboxRow").attr("hidden", true);
            $("#checkbox").prop("checked", false);
            $("#identifierRow").attr("hidden", true);
            $("#identifier").val("");
        }
    }

    checkboxChange() {
        const checked = this.checked;

        if (checked) {
            $("#identifierRow").attr("hidden", false);
        } else {
            $("#identifierRow").attr("hidden", true);
            $("#identifier").val("");
        }
    }
}
