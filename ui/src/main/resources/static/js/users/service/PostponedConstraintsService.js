/**
 * Handles the postponed-constraints section shown when assigning or editing
 * a user role: rendering constraint inputs, validating, and collecting values.
 */
class PostponedConstraintsService {
    constructor(config) {
        this.usersUrl = config.usersUrl;
        this.uiUrl = config.uiUrl;
    }

    init(scope) {
        window.kleConstraintService.init();
        window.orgUnitPostponedConstraintService.init();
        window.select2Service.initSelect(".select2ItSystemPostponed");
        window.select2Service.initSelect(".select2ComboMultiPostponed");
        window.postponedConstraintGroupingService.init(scope);
    }

    initForModal(modalSelector, scope) {
        window.kleConstraintService.init();
        window.orgUnitPostponedConstraintService.init();

        window.select2Service.initSelect(".select2ItSystemPostponed", {
            dropdownParent: $(modalSelector)
        });
        window.select2Service.initSelect(".select2ComboMultiPostponed", {
            dropdownParent: $(modalSelector)
        });
        window.postponedConstraintGroupingService.init(scope || document.querySelector(modalSelector), modalSelector);
    }

    initGroupMasterRow(masterContainer, dropdownParent) {
        const select2Config = dropdownParent ? { dropdownParent: $(dropdownParent) } : undefined;
        window.select2Service.initSelect($(masterContainer).find(".select2ItSystemPostponed"), select2Config);
        window.select2Service.initSelect($(masterContainer).find(".select2ComboMultiPostponed"), select2Config);
    }

    /**
     * Re-initializes select2 on a row's fields after it was cloned to build a group master row -
     * cloning a select2-bound <select> leaves the original element's select2 instance torn down.
     */
    reinitRowFields(row, dropdownParent) {
        if (!row) {
            return;
        }

        const select2Config = dropdownParent ? { dropdownParent: $(dropdownParent) } : undefined;

        [".select2ItSystemPostponed", ".select2ComboMultiPostponed"].forEach((selector) => {
            const field = $(row).find(selector);
            if (field.length === 0) {
                return;
            }

            if (field.hasClass("select2-hidden-accessible")) {
                field.select2("destroy");
            }
            window.select2Service.initSelect(field, select2Config);
        });
    }

    loadPostponedConstraintsFragment(roleId, onLoaded, placeholderSelector = "#postponedConstraintsPlaceholder") {
        $("#postponedConstraintsPlaceholder").empty();
        $("#editPostponedConstraintsPlaceholder").empty();
        $(placeholderSelector).empty();

        $(placeholderSelector).load(`${this.uiUrl}postponedconstraints/${roleId}`, () => {
            this.init(document.querySelector(placeholderSelector));

            const postponingAllowed = $("#postponingAllowed").val();
            if (postponingAllowed === "true") {
                $("#positionTable").hide();
            } else {
                $("#positionTable").show();
            }

            if (onLoaded) {
                onLoaded(postponingAllowed === "true");
            }
        });
    }

    validate(from) {
        $(".constraintValidationError").hide();

        const constraints = this.getConstraintList();
        $.ajax({
            url: `${this.usersUrl}constraints/validate`,
            contentType: 'application/json',
            headers: {
                'X-CSRF-TOKEN': window.token
            },
            type: 'post',
            data: JSON.stringify(constraints),
            success: () => {
                if (from === "edit") {
                    window.userRoleEditModalService.updateClicked();
                } else if (from === "assignRoleDirectlyClicked") {
                    window.userRoleModalService.assignRoleDirectlyClicked();
                } else if (from === "requestRole") {
                    window.requestRoleModalService.performRoleRequest();
                } else if (from === "requestRoleMultipleUsers") {
                    window.userService.request();
                } else if (from === "bulkAssignRoleClicked") {
                    window.bulkAssignRoleModalService.bulkAssignRoleClicked();
                }
            },
            error: (response) => {
                for (const id of response.responseJSON) {
                    $(`#postponed${id}error`).show();
                }
            }
        });
    }

    getConstraintList() {
        const constraintList = [];
        $(".constraint").each(function () {
            const field = $(this);
            const type = field.data("type");
            const systemRoleId = field.data("systemroleid");
            const constraintType = field.data("constrainttype");
            let val = field.val();

            if (Array.isArray(val)) {
                let valString = "";
                for (const element of val) {
                    valString += `${element},`;
                }
                val = valString.substring(0, valString.length - 1);
            }

            constraintList.push({
                type,
                systemRoleId,
                constraintTypeUuid: constraintType,
                value: val
            });
        });

        return constraintList;
    }
}
