/**
 * Notifications list page.
 * Handles the DataTable listing of notifications, the notification type
 * filter, the active/inactive toggle, and the assign/status change actions.
 */

const token = $("meta[name='_csrf']").attr("content");
const config = JSON.parse(document.getElementById("notifications-list-config").textContent);
const sweetAlertService = new SweetAlertService();
const datatableService = new DatatableService();

// Kept as a page-local variable, matching the original behaviour (assigned
// but never read elsewhere in this file).
let currentTable;

document.addEventListener("DOMContentLoaded", () => {
    // Restore any saved search term for the notification type column
    const datatableState = localStorage.getItem("DataTables_listTable_/ui/notifications/list");
    if (datatableState === null) {
        loadDataTables(false, "");
    } else {
        const savedState = JSON.parse(datatableState);
        const searchFor = savedState.columns[2].search.search;
        loadDataTables(false, searchFor);
    }

    $("input[type=radio][name=showInactive]").on("change", function () {
        const selectedValue = $("#notificationTypeSelect").val();
        const showInactive = this.value === "true";
        loadDataTables(showInactive, selectedValue);
    });

    // Delegated listeners replacing the old inline onclick/onClick handlers
    // that were generated inside the DataTables render functions.
    $("#listTable tbody").on("click", ".js-flip-assign", (event) => {
        event.preventDefault();
        const $link = $(event.currentTarget);
        flipAssign($link.data("id"), $link.data("status"), $link.data("admin-uuid") || "");
    });

    $("#listTable tbody").on("click", ".js-change-status", (event) => {
        event.preventDefault();
        const $link = $(event.currentTarget);
        changeStatus($link.data("id"), $link.data("status"));
    });
});

function flipAssign(id, status, currentUuid) {
    let title = config.assignTitle;
    let text = config.assignText;
    let yesBtn = config.changeStatusButtonConfirm;
    let noBtn = config.changeStatusButtonCancel;
    let reassign = false;

    // Overwrite default messages if already assigned
    if (currentUuid !== "") {
        if (currentUuid === config.userUuid) {
            title = config.deassignTitle;
            text = config.deassignText;
        } else {
            title = config.reassignTitle;
            text = config.reassignText;
            yesBtn = config.reassignYesBtn;
            noBtn = config.reassignNoBtn;
            reassign = true;
        }
    }

    // Note: for a reassign, the request must fire regardless of which button
    // is pressed (matches the original "isConfirm || reassign" behaviour),
    // so both onConfirm and the reassign branch of onCancel trigger it.
    sweetAlertService.confirm(title, text, yesBtn, noBtn, () => {
        sendFlipAssignRequest(id, status, true);
    }, {
        onCancel: () => {
            if (reassign) {
                sendFlipAssignRequest(id, status, false);
            }
        },
        customConfig: {
            confirmButtonColor: "#4765a0",
            type: undefined
        }
    });
}

function sendFlipAssignRequest(id, status, isConfirm) {
    $.ajax({
        url: config.flipAssignUrl + id,
        headers: {
            "X-CSRF-TOKEN": token,
            "confirm": isConfirm
        },
        type: "post",
        contentType: "application/json",
        data: "",
        success: () => {
            const selectedValue = $("#notificationTypeSelect").val();
            loadDataTables(status, selectedValue);
        },
        error: errorHandler(config.changeStatusErrorMsg)
    });
}

function changeStatus(id, status) {
    const title = status ? config.activateTitle : config.discardTitle;
    const text = status ? config.activateText : config.discardText;

    sweetAlertService.confirm(title, text, config.changeStatusButtonConfirm, config.changeStatusButtonCancel, () => {
        $.ajax({
            url: config.changeStatusUrl,
            headers: {
                "X-CSRF-TOKEN": token,
                "id": id,
                "status": status
            },
            type: "post",
            contentType: "application/json",
            data: "",
            success: () => {
                const selectedValue = $("#notificationTypeSelect").val();
                loadDataTables(status, selectedValue);
            },
            error: errorHandler(config.changeStatusErrorMsg)
        });
    });
}

function loadDataTables(showInactive, selectedValue) {
    const options = datatableService.getDefaultOptions_Serverside(config.ajaxUrl, {
        search: config.searchTxt,
        lengthMenu: config.dropdownTxt,
        info: config.infoDefaultTxt,
        zeroRecords: config.infoEmptyTxt,
        infoEmpty: "",
        infoFiltered: config.infoFilteredTxt,
        paginate: {
            next: config.nextTxt,
            previous: config.prevTxt
        }
    });

    // The base options add an extra "show-inactive" header, and this table's
    // original behaviour (no explicit order, default autoWidth, no search
    // delay) differs from the service defaults, so those are restored here.
    options.ajax.headers["show-inactive"] = showInactive;
    options.autoWidth = true;
    delete options.order;
    delete options.searchDelay;

    options.columns = [
        {
            data: "active",
            orderable: false,
            searchable: true,
            visible: false
        },
        {
            data: "created"
        },
        {
            data: "notificationType",
            render: (data) => config.typesMap[data]
        },
        {
            data: "affectedEntityName",
            orderable: true,
            searchable: true,
            render: (data, type, row) => {
                if (type !== "display") {
                    return data || "";
                }
                if (row.affectedEntityName == null || row.affectedEntityType == null || row.affectedEntityUuid == null) {
                    return "";
                }
                let html = row.affectedEntityName;
                if (row.canReadAffectedEntity) {
                    html += ` <a href="${config.url}${row.affectedEntityType.toLowerCase()}/manage/${row.affectedEntityUuid}"><em class="fa fa-fw fa-external-link"></em></a>`;
                }
                return html;
            }
        },
        {
            data: "adminName",
            render: (data, type, row) => {
                if (type !== "display" || !row.allowedActions.editable) {
                    return data || "";
                }
                const adminUuid = row.adminUuid || "";
                const adminInfo = row.adminName ? `${row.adminName}<br/><em style="font-size: smaller;">${row.lastUpdated}</em>` : "";
                return `<a href="#" class="js-flip-assign" data-id="${row.id}" data-status="${!row.active}" data-admin-uuid="${adminUuid}"><em class="fa fa-fw fa-user"></em></a>${adminInfo}`;
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
                if (data.readable) {
                    html += `<a href="${config.url}report/notifications/view/${row.id}"><em class="fa fa-fw fa-search"></em></a>`;
                }
                if (data.editable) {
                    const iconClass = row.active ? "times" : "repeat";
                    html += `<a href="#" class="js-change-status" data-id="${row.id}" data-status="${!row.active}"><em class="fa fa-fw fa-${iconClass}"></em></a>`;
                }
                return html;
            }
        }
    ];

    options.initComplete = function () {
        this.api().columns([2]).every(function () {
            const column = this;
            const select = $('<select class="ui-state-default" id="notificationTypeSelect"><option value="">Vis alle</option></select>')
                .appendTo($(column.footer()).empty())
                .on("change", function () {
                    column.search($(this).val(), false, true).draw();
                });

            for (const key in config.typesMap) {
                select.append(`<option value="${key}">${config.typesMap[key]}</option>`);
            }

            if (selectedValue != null) {
                $("#notificationTypeSelect").val(selectedValue);
            }
        });
    };

    currentTable = $(".listTable").DataTable(options);
}
