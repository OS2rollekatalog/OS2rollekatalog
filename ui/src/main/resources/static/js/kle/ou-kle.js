const token = $("meta[name='_csrf']").attr("content");
const jsTreeService = new JsTreeService();

document.addEventListener("DOMContentLoaded", () => {
    const config = JSON.parse(document.getElementById("oukle-report-config").textContent);

    initHierarchyTree(config.orgUnits);
    initSearchField();
});

function initHierarchyTree(orgUnits) {
    jsTreeService.initTree("#hierarchy", {
        core: {
            data: orgUnits,
            multiple: false
        },
        search: {
            show_only_matches: true,
            search_callback: (str, node) => {
                return node.text.toUpperCase().startsWith(str.toUpperCase());
            }
        },
        plugins: ["search", "state", "wholerow"]
    });
}

function initSearchField() {
    let searchTimeout = false;

    $("#searchField").keyup(() => {
        if (searchTimeout) {
            clearTimeout(searchTimeout);
        }

        searchTimeout = setTimeout(() => {
            const searchValue = $("#searchField").val();
            jsTreeService.getInstance("#hierarchy").search(searchValue);
        }, 400);
    });
}
