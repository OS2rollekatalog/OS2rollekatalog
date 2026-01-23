document.addEventListener('DOMContentLoaded', (e) => {
    // Global variable used to expose functions to non-module js script. Can be removed upon full switch to ES6 modules
    window.dataTableExtension = {
        createLinkButton: (href, className, dataAttributeMap) => createLinkButton(href, className, dataAttributeMap)
    }

})

export const PERMISSIONS = {
    CREATE: "CREATE",
    READ: "READ",
    DELETE: "DELETE",
    UPDATE: "UPDATE",
}

export class PermissionConfig {
    constructor(canCreate = false, canRead = false, canDelete = false, canUpdate = false) {
        this.createAllowed = canCreate;
        this.readAllowed = canRead;
        this.deleteAllowed = canDelete;
        this.updateAllowed = canUpdate;
    }
}


function renderActionButtons(
    onRead = null,
    onUpdate = null,
    onDelete = null,
    onDuplicate = null,
) {
    if (onRead && onRead instanceof Function) {

    }
    if (onUpdate && onUpdate instanceof Function) {

    }
    if (onDelete && onDelete instanceof Function) {

    }
    if (onDuplicate && onDuplicate instanceof Function) {

    }
}

export function createLinkButton(href, className, dataAttributeMap, onClickAction) {
    const linkElement = document.createElement("a");
    linkElement.setAttribute("href", href);

    const emElement = document.createElement("em");
    emElement.classList.add("fa");
    if (className) {
        emElement.classList.add(className);
    }

    if (dataAttributeMap && dataAttributeMap instanceof Map) {
        for (const key in dataAttributeMap) {
            linkElement.dataset[key] = dataAttributeMap[key];
        }
    }

    if (onClickAction) {
        linkElement.addEventListener("click", onClickAction);
    }

    linkElement.appendChild(emElement);
    return linkElement;
}