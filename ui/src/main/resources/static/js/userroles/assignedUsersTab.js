import {createDataTable} from '../fragments/dataTable.js';
import {showError} from '../service/notifications.js';

/**
 * The "Brugere" tab of a user role: loads the tab fragment and fills its table from the
 * holders endpoint, which sends every text as it is displayed. DataTables only builds the
 * rows of the shown page, and sorts and searches on those same texts.
 */
const TABLE_ID = "listTableUsers";

export async function loadAssignedUsersTab(container, fragmentUrl) {
    const networkService = new NetworkService();
    const reportFailure = failureReporter(container.dataset.loadErrorMessage);
    try {
        const fragment = await networkService.Get(fragmentUrl);
        container.innerHTML = await fragment.text();

        const table = document.getElementById(TABLE_ID);
        const holders = await (await networkService.Get(table.dataset.holdersUrl)).json();

        const nameTemplate = document.getElementById("holderNameCell");
        createDataTable(table, {orderColumn: 0, pageLength: 100, columns: columns(nameTemplate), data: holders, saveState: true});
    } catch (error) {
        await reportFailure(error);
    }
}

function failureReporter(fallbackMessage) {
    return async (error) => {
        console.error(`Loading the users of the role failed (status ${error?.status ?? 'none'})`, error);

        let message = fallbackMessage;
        try {
            message = (await error.response?.clone().json())?.friendlyMessage ?? fallbackMessage;
        } catch {
            // keep the fallback message
        }

        showError(escapeHtml(message));
    };
}

function escapeHtml(text) {
    const element = document.createElement("div");
    element.textContent = text;
    return element.innerHTML;
}

function columns(nameTemplate) {
    return [
        {
            data: null,
            render: (_value, type, holder) => (type === "display" ? "" : joined([holder.name, holder.disabledLabel, holder.outweighedLabel])),
            createdCell: (cell, _value, holder) => cell.replaceChildren(nameCell(nameTemplate, holder))
        },
        { data: "userId", defaultContent: "", render: $.fn.dataTable.render.text() },
        { data: "assignedThrough", defaultContent: "", render: $.fn.dataTable.render.text() },
        listColumn("validity", "list-unstyled mb0"),
        listColumn("positions", "list-unstyled"),
        {
            data: "edit",
            searchable: false,
            render: (edit, type) => (type === "display" ? "" : Number(edit.canEdit)),
            createdCell: (cell, edit) => fillOperationsCell(cell, edit)
        }
    ];
}

function listColumn(field, listClass) {
    return {
        data: field,
        render: (lines, type) => (type === "display" ? "" : joined(lines)),
        createdCell: (cell, lines) => cell.replaceChildren(list(lines, listClass))
    };
}

function joined(texts) {
    return texts.filter(text => text != null).join(" ");
}

function nameCell(template, holder) {
    const content = template.content.cloneNode(true);
    content.querySelector(".js-name").textContent = holder.name ?? "";
    showOrRemove(content.querySelector(".js-disabled"), holder.disabledLabel);
    showOrRemove(content.querySelector(".js-outweighed"), holder.outweighedLabel);
    return content;
}

function showOrRemove(element, text) {
    if (text == null) {
        element.remove();
        return;
    }
    element.textContent = text;
}

function list(lines, listClass) {
    const element = document.createElement("ul");
    element.className = listClass;
    for (const line of lines) {
        const item = document.createElement("li");
        item.textContent = line;
        element.append(item);
    }
    return element;
}

// the edit/delete handlers in view.js and edit.js read the assignment from these data attributes
function fillOperationsCell(cell, edit) {
    setData(cell, "assignmentid", edit.assignmentId);
    setData(cell, "user", edit.userUuid);
    setData(cell, "startdate", edit.startDate);
    setData(cell, "stopdate", edit.stopDate);
    setData(cell, "name", edit.roleName);
    setData(cell, "ouuuid", edit.orgUnitUuid);
    setData(cell, "casenumber", edit.caseNumber);

    if (edit.canEdit) {
        cell.replaceChildren(iconLink("js-edit-user-assignment", "fa-pencil"), " ", iconLink("js-delete-user-assignment", "fa-times"));
    }
}

function setData(element, key, value) {
    if (value != null) {
        element.dataset[key] = value;
    }
}

function iconLink(linkClass, iconClass) {
    const link = document.createElement("a");
    link.href = "#";
    link.className = linkClass;
    const icon = document.createElement("em");
    icon.className = `fa ${iconClass}`;
    link.append(icon);
    return link;
}

// view.js and edit.js are classic scripts and cannot import this module
window.loadAssignedUsersTab = loadAssignedUsersTab;
