/**
 * Controls the 3-step "assign role (or role group) to multiple users" flow:
 * dates/case number, then eligible-users selection, then per-user responsible
 * org unit, finally submitting one bulk request for all selected users.
 * Shared between userroles and rolegroups; `roleType` picks which REST paths
 * to hit ("userrole": available/{id} + bulkaddrole/{id}, "rolegroup":
 * available/rolegroup/{id} + bulkaddrolegroup/{id}).
 */
class BulkAssignRoleModalService {
    constructor(config) {
        this.usersUrl = config.usersUrl;
        this.uiUrl = config.uiUrl;
        this.fieldUpdatedMsg = config.fieldUpdatedMsg;
        this.fieldNotUpdatedMsg = config.fieldNotUpdatedMsg;
        this.caseNumberEnabled = config.caseNumberEnabled;
        this.selectedCountLabel = config.selectedCountLabel;
        this.userRoleModalTitle = config.userRoleModalTitle;
        this.roleGroupModalTitle = config.roleGroupModalTitle;
        this.parentService = null;

        this.roleId = null;
        this.roleName = null;
        this.roleType = "userrole";
        this.startDate = null;
        this.stopDate = null;
        this.caseNumber = null;
        this.selectedUsers = new Map();
        this.orgUnitsByUser = new Map();
    }

    availableUsersUrl() {
        return this.roleType === "rolegroup"
            ? `${this.usersUrl}available/rolegroup/${this.roleId}`
            : `${this.usersUrl}available/${this.roleId}`;
    }

    bulkAddUrl() {
        return this.roleType === "rolegroup"
            ? `${this.usersUrl}bulkaddrolegroup/${this.roleId}`
            : `${this.usersUrl}bulkaddrole/${this.roleId}`;
    }

    init() {
        $("#bulkAssignDatesNextBtn").on("click", () => this.datesNextClicked());
        $("#closeBulkAssignDatesModal").on("click", () => $("#modal-bulk-assign-dates").modal("hide"));

        $("#bulkAssignUsersNextBtn").on("click", () => this.usersNextClicked());
        $("#bulkAssignUsersBackBtn").on("click", () => this.usersBackClicked());
        $("#closeBulkAssignUsersModal").on("click", () => $("#modal-bulk-assign-users").modal("hide"));

        $("#listTableBulkAssignUsers tbody").on("change", ".js-bulk-assign-user-checkbox", (event) => this.userCheckboxToggled(event.currentTarget));

        $("#bulkAssignFinishBtn").on("click", () => this.finishClicked());
        $("#bulkAssignOrgUnitsBackBtn").on("click", () => this.orgUnitsBackClicked());
        $("#closeBulkAssignOrgUnitsModal").on("click", () => $("#modal-bulk-assign-orgunits").modal("hide"));
        $("#bulkAssignOrgUnitsTableBody").on("change", ".js-bulk-assign-orgunit-select", (event) => this.orgUnitSelected(event.currentTarget));

        $(".bulk_assign_role").on("click", () => {
            window.postponedConstraintsService.validate("bulkAssignRoleClicked");
        });
        $("#bulkAssignConstraintsBackBtn").on("click", () => this.constraintsBackClicked());
        $("#closeBulkAssignConstraintsModal").on("click", () => $("#modal-bulk-assign-constraints").modal("hide"));
    }

    start(roleId, roleName, roleType = "userrole") {
        this.roleId = roleId;
        this.roleName = roleName;
        this.roleType = roleType;
        this.startDate = null;
        this.stopDate = null;
        this.caseNumber = null;
        this.selectedUsers = new Map();
        this.orgUnitsByUser = new Map();

        $("#bulkAssignDatesModalTitle").text(roleType === "rolegroup" ? this.roleGroupModalTitle : this.userRoleModalTitle);
        $("#bulkAssignUserRoleName").text(roleName);
        $("#bulkAssignRoleCaseNumber").val("");

        $("#stopDatePickerBulk").data("DateTimePicker").clear();
        $("#startDatePickerBulk").data("DateTimePicker").clear();
        $("#startDatePickerBulk").data("DateTimePicker").date(new Date());

        $("#modal-bulk-assign-dates").modal({ backdrop: "static", keyboard: false });
    }

    datesNextClicked() {
        this.startDate = $("#startDatePickerBulk").data("date");
        this.stopDate = $("#stopDatePickerBulk").data("date");

        if (this.caseNumberEnabled) {
            this.caseNumber = $("#bulkAssignRoleCaseNumber").val();
        }

        $("#modal-bulk-assign-dates").modal("hide");
        this.loadEligibleUsersTable();
    }

    loadEligibleUsersTable(resetSelection = true) {
        if (resetSelection) {
            this.selectedUsers = new Map();
        }
        this.updateSelectedCount();

        const columnDefOptions = [
            {
                targets: [0],
                data: "uuid",
                orderable: false,
                searchable: false,
                sortable: false,
                render: (data, type, row) => {
                    if (row.isAlreadyAssigned || row.disabled) {
                        return "";
                    }

                    const checked = this.selectedUsers.has(data) ? "checked" : "";
                    const escapedName = $("<div>").text(row.name).html();
                    return `
                        <div class="checkbox c-checkbox">
                            <label>
                                <input type="checkbox" class="js-bulk-assign-user-checkbox" value="${data}" data-name="${escapedName}" ${checked}/>
                                <span class="fa fa-check"></span>
                            </label>
                        </div>
                    `;
                }
            },
            {
                targets: [1],
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
                targets: [2],
                data: "userId"
            },
            {
                targets: [3],
                data: "positions",
                render: (data) => {
                    let html = '<ul style="list-style: none;">';
                    for (const position of data) {
                        html += `<li>${position.name} i ${position.orgUnit.name}</li>`;
                    }
                    html += "</ul>";
                    return html;
                }
            }
        ];

        this.usersTable = new DatatableService().initDefaultServersideTable(
            "#listTableBulkAssignUsers",
            this.availableUsersUrl(),
            columnDefOptions
        );

        if (this.usersTable.page.len() !== 10 && this.usersTable.page.len() !== 25) {
            this.usersTable.page.len(10).draw();
        }

        $("#modal-bulk-assign-users").modal({ backdrop: "static", keyboard: false });
    }

    userCheckboxToggled(checkbox) {
        this.setUserSelected(checkbox.value, checkbox.dataset.name, checkbox.checked);
        this.updateSelectedCount();
    }

    setUserSelected(uuid, name, selected) {
        if (selected) {
            this.selectedUsers.set(uuid, name);
        } else {
            this.selectedUsers.delete(uuid);
        }
    }

    updateSelectedCount() {
        $("#bulkAssignSelectedCount").text(`${this.selectedUsers.size} ${this.selectedCountLabel}`);
        $("#bulkAssignUsersNextBtn").prop("disabled", this.selectedUsers.size === 0);
    }

    usersNextClicked() {
        if (this.selectedUsers.size === 0) {
            return;
        }

        $("#modal-bulk-assign-users").modal("hide");

        $("#bulkAssignOrgUnitsRoleName").text(this.roleName);
        this.loadOrgUnitsTable();
    }

    usersBackClicked() {
        $("#modal-bulk-assign-users").modal("hide");
        $("#modal-bulk-assign-dates").modal({ backdrop: "static", keyboard: false });
    }

    orgUnitsBackClicked() {
        $("#modal-bulk-assign-orgunits").modal("hide");
        this.loadEligibleUsersTable(false);
    }

    constraintsBackClicked() {
        $("#modal-bulk-assign-constraints").modal("hide");
        $("#modal-bulk-assign-orgunits").modal({ backdrop: "static", keyboard: false });
    }

    loadOrgUnitsTable() {
        const tbody = $("#bulkAssignOrgUnitsTableBody");
        tbody.empty();

        const userUuids = Array.from(this.selectedUsers.keys());
        const orgUnitRequests = userUuids.map((uuid) => $.ajax({
            method: "GET",
            url: `${this.usersUrl}${uuid}/orgunits`
        }));

        $.when(...orgUnitRequests).then((...responses) => {
            // $.when with a single request resolves with (data, status, xhr) directly
            // instead of an array of such tuples, so normalize to always have an array.
            const results = userUuids.length === 1 ? [responses] : responses;

            const escapeHtml = (text) => $("<div>").text(text).html();

            userUuids.forEach((uuid, index) => {
                const orgUnits = results[index][0];
                const name = this.selectedUsers.get(uuid);

                let optionsHtml = "";
                if (orgUnits.length !== 1) {
                    optionsHtml += `<option value="" selected>&nbsp;</option>`;
                }
                for (const orgUnit of orgUnits) {
                    optionsHtml += `<option value="${orgUnit.uuid}">${escapeHtml(orgUnit.name)}</option>`;
                }

                const selectedValue = orgUnits.length === 1 ? orgUnits[0].uuid : "";

                tbody.append(`
                    <tr data-uuid="${uuid}">
                        <td>${escapeHtml(name)}</td>
                        <td>
                            <select class="form-control js-bulk-assign-orgunit-select" data-uuid="${uuid}">
                                ${optionsHtml}
                            </select>
                        </td>
                    </tr>
                `);

                tbody.find(`tr[data-uuid="${uuid}"] select`).val(selectedValue);
                if (selectedValue) {
                    this.setOrgUnitSelected(uuid, selectedValue);
                }
            });

            $("#modal-bulk-assign-orgunits").modal({ backdrop: "static", keyboard: false });
        });
    }

    orgUnitSelected(select) {
        this.setOrgUnitSelected(select.dataset.uuid, select.value);
    }

    setOrgUnitSelected(uuid, orgUnitUuid) {
        this.orgUnitsByUser.set(uuid, orgUnitUuid);
    }

    finishClicked() {
        $("#modal-bulk-assign-orgunits").modal("hide");

        // Rolegroups never support postponed constraints, so skip straight to submit.
        if (this.roleType === "rolegroup") {
            this.submitBulkAssignment([]);
            return;
        }

        $("#bulkAssignConstraintsRoleName").text(this.roleName);

        window.postponedConstraintsService.loadPostponedConstraintsFragment(this.roleId, (postponingAllowed) => {
            if (postponingAllowed) {
                $("#modal-bulk-assign-constraints").modal({ backdrop: "static", keyboard: false });
            } else {
                this.submitBulkAssignment([]);
            }
        }, "#bulkAssignPostponedConstraintsPlaceholder");
    }

    submitBulkAssignment(postponedConstraints) {
        const userUuids = Array.from(this.selectedUsers.keys());
        const orgUnitsByUser = this.orgUnitsByUser;

        const userAssignments = userUuids.map((uuid) => ({
            userUuid: uuid,
            orgUnitUuid: orgUnitsByUser.get(uuid)
        }));

        $.ajax({
            url: this.bulkAddUrl(),
            contentType: "application/json",
            headers: {
                "X-CSRF-TOKEN": window.token
            },
            type: "post",
            data: JSON.stringify({
                userAssignments,
                startDate: this.startDate,
                stopDate: this.stopDate,
                caseNumber: this.caseNumber,
                postponedConstraints
            }),
            success: (result) => {
                if (result.failures && result.failures.length > 0) {
                    const escapeHtml = (text) => $("<div>").text(text).html();
                    const failureLines = result.failures.map((failure) => {
                        const name = this.selectedUsers.get(failure.userUuid) || "Ukendt bruger";
                        return `${escapeHtml(name)}: ${escapeHtml(failure.message)}`;
                    });
                    const header = `${this.fieldNotUpdatedMsg} (${result.failures.length})`;
                    window.modalAjaxService.showErrorNotification(
                        `${header}<br>${failureLines.join("<br>")}`
                    );
                } else {
                    window.modalAjaxService.showInfoNotification(this.fieldUpdatedMsg);
                }
                this.parentService.loadRolesFragmentWhenReady();
            },
            error: window.errorHandler(this.fieldNotUpdatedMsg)
        });

        $("#modal-bulk-assign-constraints").modal("hide");
    }

    bulkAssignRoleClicked() {
        const constraints = window.postponedConstraintsService.getConstraintList();
        this.submitBulkAssignment(constraints);
    }
}
