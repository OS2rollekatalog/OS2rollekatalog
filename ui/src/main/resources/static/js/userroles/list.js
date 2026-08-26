// List page for user roles: server-side DataTable with sync-status badges,
// requester/approver permission columns, localStorage-based state
// restoration, and the delete confirmation dialog.

let pageConfig;

document.addEventListener("DOMContentLoaded", () => {
    pageConfig = JSON.parse(document.getElementById("userroles-list-config").textContent);
    window.token = $("meta[name='_csrf']").attr("content");
    window.sweetAlertService = window.sweetAlertService || new SweetAlertService();

    initFooterSearchInputs();
    const table = initDataTable();
    restoreState(table);
    applyFooterSearch(table);
    initDeleteDialog();
});

/**
 * Replaces {0}, {1}, etc. placeholders in a template string with the given arguments.
 * @param {string} template
 * @param {...string} args
 * @returns {string}
 */
function formatString(template, ...args) {
    return template.replace(/(\{\d+\})/g, (match) => {
        const index = Number(match.substr(1, match.length - 2)) || 0;
        return args[index];
    });
}

function initFooterSearchInputs() {
    $('#listTable tfoot th[class="input-filter"]').each(function () {
        $(this).html('<input type="text" class="form-control input-sm" placeholder="Søg" />');
    });
}

function initDataTable() {
    return $("#listTable").DataTable({
        destroy: true,
        stateSave: true,
        stateLoadCallback: (settings) => {
            try {
                const data = JSON.parse(localStorage.getItem(`DataTables_${settings.sInstance}`));

                if (data && data.columns) {
                    // Force correct visibility for columns 3 and 4 (requester/approver permission)
                    data.columns[3].visible = pageConfig.requestApproveEnabled;
                    data.columns[4].visible = pageConfig.requestApproveEnabled;
                }

                return data;
            } catch (error) {
                return null;
            }
        },
        stateSaveCallback: (settings, data) => {
            // Always save current requestApproveEnabled state
            if (data.columns) {
                data.columns[3].visible = pageConfig.requestApproveEnabled;
                data.columns[4].visible = pageConfig.requestApproveEnabled;
            }
            localStorage.setItem(`DataTables_${settings.sInstance}`, JSON.stringify(data));
        },
        ajax: {
            contentType: "application/json",
            url: pageConfig.ajaxUrl,
            type: "POST",
            headers: {
                "X-CSRF-TOKEN": window.token
            },
            data: (d) => JSON.stringify(d)
        },
        serverSide: true,
        columns: [
            {
                data: "name",
                orderable: true,
                searchable: true,
                render: (data, type, row) => {
                    let result = "";

                    if (row.pendingSync === true && row.syncFailed === false) {
                        result += `<em class="fa fa-refresh" title="${pageConfig.titleSyncing}"></em> `;
                    }

                    if (row.pendingSync === true && row.syncFailed === true) {
                        result += `<em class="fa fa-exclamation-triangle" style="color: red;" title="${pageConfig.titleFailed}"></em> `;
                    }

                    result += `<span>${data}</span>`;

                    // Add AD group badges
                    if (row.adGroupNames && row.adGroupNames.length > 0) {
                        row.adGroupNames.forEach((groupName) => {
                            result += ` <span class="badge badge-success" title="AD Gruppe">AD gruppe: ${groupName}</span>`;
                        });
                    }

                    return result;
                }
            },
            {
                data: "itSystemName",
                orderable: true,
                searchable: true
            },
            {
                data: "description",
                orderable: true,
                searchable: true,
                className: "preformat"
            },
            {
                data: "effectiveRequesterPermission",
                orderable: true,
                searchable: true,
                visible: pageConfig.requestApproveEnabled,
                render: (data, type) => {
                    if (!pageConfig.requestApproveEnabled) {
                        return "";
                    }
                    if (type === "display") {
                        if (data) {
                            return data
                                .map((p) => pageConfig.requestableBy[p.trim()] || p.trim())
                                .join(", ");
                        }
                        return pageConfig.requestableBy.INHERIT;
                    }
                    return data;
                }
            },
            {
                data: "effectiveApproverPermission",
                orderable: true,
                searchable: true,
                visible: pageConfig.requestApproveEnabled,
                render: (data, type) => {
                    if (!pageConfig.requestApproveEnabled) {
                        return "";
                    }
                    if (type === "display") {
                        if (data) {
                            return data
                                .map((p) => pageConfig.approvableBy[p.trim()] || p.trim())
                                .join(", ");
                        }
                        return pageConfig.approvableBy.INHERIT;
                    }
                    return data;
                }
            },
            {
                data: "allowedActions",
                orderable: false,
                searchable: false,
                render: (data, type, row) => {
                    if (type !== "display") {
                        return data;
                    }

                    let html = "";
                    html += data.readable ? `<a href="${pageConfig.baseUrl}ui/userroles/view/${row.id}"><em class="fa fa-search"></em></a>` : "";
                    html += data.editable ? `<a href="${pageConfig.baseUrl}ui/userroles/edit/${row.id}"><em class="fa fa-pencil"></em></a>` : "";
                    html += data.deletable ? `<a href="#" class="openConfirmDeleteDialog" data-id="${row.id}"><em class="fa fa-trash"></em></a>` : "";
                    html += data.duplicateable ? `<a href="${pageConfig.baseUrl}ui/userroles/copy/${row.id}" data-id="${row.id}"><em class="fa fa-clone"></em></a>` : "";
                    return html;
                }
            }
        ],
        paging: true,
        ordering: true,
        info: true,
        pageLength: 25,
        language: {
            search: pageConfig.searchTxt,
            lengthMenu: pageConfig.dropdownTxt,
            info: pageConfig.infoDefaultTxt,
            zeroRecords: pageConfig.infoEmptyTxt,
            infoEmpty: "",
            infoFiltered: pageConfig.infoFilteredTxt
        },
        initComplete: () => {
            const footerRow = $("#listTable tfoot tr");
            footerRow.find("th").each(function () {
                $(this).css("padding", 8);
            });
            $("#listTable thead").append(footerRow);
            $("#search_0").css("text-align", "center");
        }
    });
}

function restoreState(table) {
    const state = table.state.loaded();
    if (state) {
        table.columns().eq(0).each((colIdx) => {
            const colSearch = state.columns[colIdx].search;

            if (colSearch.search) {
                $("input", table.column(colIdx).footer()).val(colSearch.search);
            }
        });

        table.draw();
    }
}

function applyFooterSearch(table) {
    $.each($(".input-filter", table.table().footer()), function () {
        const column = table.column($(this).index());
        $("input", this).on("keyup change", function () {
            if (column.search() !== this.value) {
                column.search(this.value).draw();
            }
        });
    });
}

function initDeleteDialog() {
    document.body.addEventListener("click", (event) => {
        const trigger = event.target.closest(".openConfirmDeleteDialog");
        if (!trigger) {
            return;
        }

        event.preventDefault();

        const id = trigger.dataset.id;
        const tryDeleteUrl = pageConfig.tryDeleteUrl + id;
        const deleteUrl = pageConfig.deleteUrl + id;
        let bodyTxt = pageConfig.bodyTxt;

        $.ajax({
            url: tryDeleteUrl,
            cache: false,
            error: errorHandler(pageConfig.errorMsg),
            success: (result) => {
                if (result.success === false) {
                    const ous = `Enheder: ${result.ous}`;
                    const users = `Brugere: ${result.users}`;
                    const rolegroups = `Rollebuketter: ${result.roleGroups}`;
                    bodyTxt = formatString(pageConfig.bodyTxtAdditional, ous, users, rolegroups);
                }

                window.sweetAlertService.confirm(
                    pageConfig.titleTxt,
                    bodyTxt,
                    pageConfig.confirmTxt,
                    pageConfig.cancelTxt,
                    () => {
                        $.ajax({
                            method: "POST",
                            headers: {
                                "X-CSRF-TOKEN": window.token
                            },
                            url: deleteUrl,
                            success: () => {
                                window.location.href = pageConfig.listUrl;
                            },
                            error: defaultErrorHandler
                        });
                    }
                );
            }
        });
    });
}
