document.addEventListener("DOMContentLoaded", () => {
    const configElement = document.getElementById("ous-list-config");
    const config = JSON.parse(configElement.textContent);

    $("#sidebar").affix({
        offset: {
            top: 0
        }
    });

    window.jsTreeService.initTree("#hierarchy", {
        core: {
            data: config.allOUs
        },
        search: {
            show_only_matches: true,
            search_callback: (str, node) => {
                return node.text.toUpperCase().includes(str.toUpperCase());
            }
        },
        // Must stay a regular function - jsTree calls sort with the tree instance as `this`
        sort: function (a, b) {
            const nodeA = this.get_node(a);
            const nodeB = this.get_node(b);
            return (nodeA.text > nodeB.text) ? 1 : -1;
        },
        plugins: ["sort", "state", "wholerow", "search"]
    });

    window.jsTreeService.onSelectNode("#hierarchy", (event, data) => {
        $("#ouName").text(data.node.text);
        const editButton = document.getElementById("ouEditLink");

        if (editButton && data.node.original.editable === false) {
            editButton.href = config.url + "view/" + data.node.id;
            editButton.children[0].textContent = "Vis";
        } else if (editButton) {
            editButton.href = config.url + "manage/" + data.node.id;
            editButton.children[0].textContent = "Rediger";
        }
    });

    let searchTimeout = null;
    const searchField = document.getElementById("searchField");

    searchField.addEventListener("keyup", () => {
        if (searchTimeout) {
            clearTimeout(searchTimeout);
        }

        searchTimeout = setTimeout(() => {
            const searchValue = searchField.value;
            window.jsTreeService.getInstance("#hierarchy").search(searchValue);
        }, 400);
    });
});
